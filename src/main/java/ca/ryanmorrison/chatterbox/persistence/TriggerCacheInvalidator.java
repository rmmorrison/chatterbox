package ca.ryanmorrison.chatterbox.persistence;

import ca.ryanmorrison.chatterbox.persistence.entity.Trigger;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

@Component
public class TriggerCacheInvalidator {

    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    @PostPersist
    @PostUpdate
    @PostRemove
    @CacheEvict(value = "triggers", allEntries = true)
    public void invalidateCache(Trigger trigger) {
        LOGGER.debug("Invalidating trigger cache");
    }
}
