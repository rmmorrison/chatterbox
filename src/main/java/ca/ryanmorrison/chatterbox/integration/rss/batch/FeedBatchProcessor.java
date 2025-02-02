package ca.ryanmorrison.chatterbox.integration.rss.batch;

import ca.ryanmorrison.chatterbox.integration.rss.service.FeedService;
import ca.ryanmorrison.chatterbox.persistence.entity.Feed;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndPerson;
import com.rometools.rome.io.FeedException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    private final Map<Integer, Instant> lastFeedUpdate = new HashMap<>();

    public FeedBatchProcessor(@Autowired JDA jda, @Autowired FeedService feedService) {
        this.jda = jda;
        this.feedService = feedService;
    }

    @Scheduled(fixedDelayString = "${chatterbox.rssFeeds.updateInterval:300000}")
    public void process() {
        log.debug("Checking for updated RSS feeds");

        List<Feed> feeds = feedService.get();
        feeds.forEach(feed -> {
            log.debug("Updating feed {} from channel {}", feed.getId(), feed.getChannelId());

            SyndFeed latest;
            try {
                latest = feedService.get(feed.getUrl());
            } catch (IOException | InterruptedException | FeedException e) {
                log.error("An error occurred while fetching the latest feed for {}", feed.getUrl(), e);
                return;
            }

            Instant lastPublished = latest.getPublishedDate().toInstant();
            if (!lastFeedUpdate.containsKey(feed.getId())) {
                log.debug("No previous update time found for feed {}, updating cache", feed.getUrl());
                lastFeedUpdate.put(feed.getId(), lastPublished);
                return;
            }

            if (lastPublished.equals(lastFeedUpdate.get(feed.getId()))) {
                log.debug("Feed {} has not been updated since last check", feed.getUrl());
                return;
            }

            log.debug("Feed {} has been updated since last check, sending messages", feed.getUrl());
            latest.getEntries().stream()
                    .filter(entry -> entry.getPublishedDate().toInstant().isAfter(lastFeedUpdate.get(feed.getId())))
                    .forEach(entry -> emit(feed.getChannelId(), entry, latest.getTitle(), latest.getIcon().getUrl()));
        });
    }

    private void emit(long channelId, SyndEntry entry, String feedName, String thumbnail) {
        MessageEmbed embed = new EmbedBuilder()
                .setTitle(entry.getTitle())
                .setAuthor(feedName)
                .setUrl(entry.getLink())
                .setDescription(entry.getDescription().getValue())
                .setTimestamp(entry.getPublishedDate().toInstant())
                .setThumbnail(thumbnail)
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
