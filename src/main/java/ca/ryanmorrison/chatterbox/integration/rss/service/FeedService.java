package ca.ryanmorrison.chatterbox.integration.rss.service;

import ca.ryanmorrison.chatterbox.constants.RSSConstants;
import ca.ryanmorrison.chatterbox.exception.DuplicateResourceException;
import ca.ryanmorrison.chatterbox.exception.ResourceNotFoundException;
import ca.ryanmorrison.chatterbox.persistence.entity.Feed;
import ca.ryanmorrison.chatterbox.persistence.repository.FeedRepository;
import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.MutableRequest;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;

@Component
public class FeedService {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final FeedRepository feedRepository;
    private final Methanol client;

    public FeedService(@Autowired FeedRepository feedRepository) {
        this.feedRepository = feedRepository;
        this.client = Methanol.newBuilder()
                .defaultHeader("Accept", "application/xml")
                .requestTimeout(Duration.ofSeconds(20))
                .headersTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(5))
                .autoAcceptEncoding(true)
                .build();
    }

    public SyndFeed get(String url) throws IOException, InterruptedException, FeedException {
        log.debug("Fetching RSS feed from {}", url);
        InputStream response = client.send(MutableRequest.GET(url), BodyHandlers.ofInputStream()).body();
        return new SyndFeedInput().build(new InputStreamReader(response));
    }

    @Transactional
    @Cacheable(RSSConstants.RSS_CACHE_NAME)
    public List<Feed> get() {
        return feedRepository.findAll();
    }

    @Transactional
    public List<Feed> get(long channelId) {
        return feedRepository.findAllByChannelId(channelId);
    }

    @Transactional
    public void add(long channelId, long userId, SyndFeed feed) throws DuplicateResourceException {
        String url = feed.getLink();
        if (feedRepository.findByChannelIdAndUrl(channelId, url).isPresent()) {
            throw new DuplicateResourceException("RSS feed already exists for this channel.", "RSSFeed", url);
        }

        feedRepository.save(new Feed.Builder()
                .setChannelId(channelId)
                .setUserId(userId)
                .setUrl(url)
                .setTitle(feed.getTitle())
                .build());
    }

    @Transactional
    public void delete(long channelId, String url) throws ResourceNotFoundException {
        Feed feed = feedRepository.findByChannelIdAndUrl(channelId, url)
                .orElseThrow(() -> new ResourceNotFoundException("RSS feed does not exist for this channel.", "RSSFeed", url));
        feedRepository.delete(feed);
    }
}
