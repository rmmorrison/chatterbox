package ca.ryanmorrison.chatterbox.persistence;

import ca.ryanmorrison.chatterbox.constants.RSSConstants;
import ca.ryanmorrison.chatterbox.persistence.entity.Feed;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Component
public class RSSCacheInvalidator {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final CacheManager cacheManager;

    public RSSCacheInvalidator(@Autowired CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @PostPersist
    @PostUpdate
    @PostRemove
    public void invalidateCache(Feed feed) {
        log.debug("RSS feed has been modified/deleted, checking if cache needs invalidation");
        Cache feedCache = cacheManager.getCache(RSSConstants.RSS_CACHE_NAME);
        if (feedCache != null) {
            log.debug("Invalidating RSS feed cache");
            feedCache.clear();
        }
    }
}
