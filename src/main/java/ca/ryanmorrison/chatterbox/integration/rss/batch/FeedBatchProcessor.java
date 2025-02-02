package ca.ryanmorrison.chatterbox.integration.rss.batch;

import ca.ryanmorrison.chatterbox.integration.rss.service.FeedService;
import ca.ryanmorrison.chatterbox.persistence.entity.Feed;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndImage;
import com.rometools.rome.feed.synd.SyndPerson;
import com.rometools.rome.io.FeedException;
import jakarta.annotation.PreDestroy;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class FeedBatchProcessor {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final JDA jda;
    private final FeedService feedService;
    private final TransactionTemplate transactionTemplate;

    private final Map<Integer, Instant> lastPublishedCache = new HashMap<>();

    public FeedBatchProcessor(@Autowired JDA jda,
                              @Autowired FeedService feedService,
                              @Autowired PlatformTransactionManager platformTransactionManager) {
        this.jda = jda;
        this.feedService = feedService;
        this.transactionTemplate = new TransactionTemplate(platformTransactionManager);
    }

    @Scheduled(fixedDelayString = "${chatterbox.rssFeeds.updateInterval:300000}")
    public void process() {
        log.debug("Checking for updated RSS feeds");

        List<Feed> feeds = feedService.get();
        feeds.forEach(feed -> {
            log.debug("Updating feed {} from channel {}", feed.getId(), feed.getChannelId());

            SyndFeed response;
            try {
                response = feedService.get(feed.getUrl());
            } catch (IOException | InterruptedException | FeedException e) {
                log.error("An error occurred while fetching the latest feed for {}", feed.getUrl(), e);
                return;
            }

            Instant responseLastPublished = response.getPublishedDate().toInstant();
            Instant cacheValue = lastPublishedCache.computeIfAbsent(feed.getId(), id -> {
                log.debug("No previous update time found for feed {} in cache, checking entity for a previous save", feed.getUrl());
                Instant entityLastUpdated = feed.getUpdated();
                if (entityLastUpdated == null) {
                    log.debug("No previous update time found for feed {} in entity, using last published value from feed and updating cache", feed.getUrl());
                    return responseLastPublished;
                } else {
                    log.debug("Found previous update time for feed {} in entity, updating cache", feed.getUrl());
                    return entityLastUpdated;
                }
            });

            if (responseLastPublished.equals(cacheValue)) {
                log.debug("Feed {} has not been updated since last check", feed.getUrl());
                return;
            }

            log.debug("Feed {} has been updated since last check, sending messages", feed.getUrl());
            response.getEntries().stream()
                    .filter(entry -> entry.getUpdatedDate() == null) // ignore updated entries, we only care about new ones
                    .filter(entry -> entry.getPublishedDate().toInstant().isAfter(cacheValue))
                    .forEach(entry -> emit(feed.getChannelId(), entry, response.getTitle(), response.getIcon()));
            lastPublishedCache.put(feed.getId(), responseLastPublished);
        });
    }

    @Scheduled(fixedDelayString = "${chatterbox.rssFeeds.writebackInterval:3600000}")
    public void flushCache() {
        log.debug("Flushing RSS feed cache to database");

        if (lastPublishedCache.isEmpty()) {
            log.debug("No cache entries to flush, nothing to do");
            return;
        }

        transactionTemplate.executeWithoutResult(status ->
                lastPublishedCache.forEach(feedService::update));

        log.debug("{} cache entries flushed to database", lastPublishedCache.size());
    }

    @PreDestroy
    public void handleShutdown() {
        log.debug("Shutting down RSS feed processor, triggering flush of cache to database");
        flushCache();
    }

    private void emit(long channelId, SyndEntry entry, String feedName, SyndImage thumbnail) {
        MessageEmbed embed = new EmbedBuilder()
                .setTitle(entry.getTitle())
                .setAuthor(feedName)
                .setUrl(entry.getLink())
                .setDescription(entry.getDescription().getValue())
                .setTimestamp(entry.getPublishedDate().toInstant())
                .setThumbnail(thumbnail != null ? thumbnail.getUrl() : null)
                .setFooter(entry.getAuthors().stream()
                        .map(SyndPerson::getName)
                        .collect(Collectors.joining(", ")))
                .build();

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            log.warn("Channel ID {} not found, unable to send RSS feed message", channelId);
            return;
        }
        channel.sendMessageEmbeds(embed).queue();
    }
}
