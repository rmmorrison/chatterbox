package ca.ryanmorrison.chatterbox.persistence;

import ca.ryanmorrison.chatterbox.constants.TriggerConstants;
import ca.ryanmorrison.chatterbox.persistence.entity.Trigger;
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
public class TriggerCacheInvalidator {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final CacheManager cacheManager;

    public TriggerCacheInvalidator(@Autowired CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @PostPersist
    @PostUpdate
    @PostRemove
    public void invalidateCache(Trigger trigger) {
        long channelId = trigger.getChannelId();

        log.debug("Trigger entity has been modified/deleted in channel ID {}, checking if cache needs invalidation", channelId);
        Cache channelCache = cacheManager.getCache(TriggerConstants.TRIGGER_CACHE_NAME);
        if (channelCache != null) {
            log.debug("Invalidating trigger cache for channel ID {}", channelId);
            channelCache.evict(channelId);
        }
    }
}
