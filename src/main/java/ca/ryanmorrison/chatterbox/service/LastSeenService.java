package ca.ryanmorrison.chatterbox.service;

import ca.ryanmorrison.chatterbox.persistence.entity.LastSeen;
import ca.ryanmorrison.chatterbox.persistence.repository.LastSeenRepository;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class LastSeenService {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final long maxIdleTime;
    private final ConcurrentMap<Long, LastSeen> cache;

    @PersistenceContext
    private EntityManager entityManager;
    private TransactionTemplate transactionTemplate;
    private final LastSeenRepository lastSeenRepository;

    public LastSeenService(@Autowired LastSeenRepository lastSeenRepository,
                           @Autowired PlatformTransactionManager platformTransactionManager,
                           @Value("${chatterbox.lastSeen.maxIdleTime:30}") long maxIdleTime) {
        this.lastSeenRepository = lastSeenRepository;
        this.transactionTemplate = new TransactionTemplate(platformTransactionManager);
        this.maxIdleTime = maxIdleTime;
        this.cache = new ConcurrentHashMap<>();
    }

    @Transactional
    public boolean update(long userId, OffsetDateTime timestamp) {
        boolean shouldWelcome = false;

        LastSeen cacheResult = cache.computeIfAbsent(userId, key -> {
            log.debug("Cache has no entry for user {}, querying database", userId);
            Optional<LastSeen> lastSeen = lastSeenRepository.findFirstByUserId(userId);
            if (lastSeen.isEmpty()) {
                log.debug("Database also has no entry for user {}, persisting new object to cache", userId);
                return new LastSeen.Builder()
                        .setUserId(userId)
                        .setLastSeen(timestamp.toInstant())
                        .build();
            }

            return lastSeen.get();
        });

        Duration between = Duration.between(cacheResult.getLastSeen(), timestamp);
        log.debug("User {} was last seen {}, comparing to configured maximum days {}", userId, formatDuration(between), maxIdleTime);

        if (between.toDays() >= maxIdleTime) {
            log.debug("User {} has exceed the max idle time, will send welcome back message", userId);
            shouldWelcome = true;
        }

        log.debug("Updating last seen entry for user {}", userId);
        cacheResult.setLastSeen(timestamp.toInstant());
        cache.put(userId, cacheResult);

        return shouldWelcome;
    }

    @Scheduled(fixedDelayString = "${chatterbox.lastSeen.writebackInterval:3600000}")
    public void persistCache() {
        log.info("Writing cache to database");
        transactionTemplate.executeWithoutResult(status -> {
            Session session = entityManager.unwrap(Session.class);
            cache.forEach((userId, seenObject) -> {
                if (seenObject.getId() == 0) {
                    // new object, not managed by Hibernate - save as new
                    log.debug("Saving new last seen reference for user {} to database", userId);
                    lastSeenRepository.save(seenObject);
                    return;
                }

                log.debug("Reattaching last seen entity and saving for user {} to database", userId);
                session.merge(seenObject);
            });
        });
    }

    @PreDestroy
    public void persistCacheOnShutdown() {
        log.debug("Bean being destroyed - persisting cache to database before shutdown");
        persistCache();
    }

    private String formatDuration(Duration duration) {
        if (duration.getSeconds() < 60) {
            return duration.getSeconds() + " seconds ago";
        } else if (duration.toMinutes() < 60) {
            return duration.toMinutes() + " minutes ago";
        } else if (duration.toHours() < 24) {
            return duration.toHours() + " hours ago";
        } else {
            return duration.toDays() + " days ago";
        }
    }
}
