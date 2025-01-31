package ca.ryanmorrison.chatterbox.service;

import ca.ryanmorrison.chatterbox.persistence.entity.LastSeen;
import ca.ryanmorrison.chatterbox.persistence.repository.LastSeenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class LastSeenService {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final long maxIdleTime;

    private final LastSeenRepository lastSeenRepository;

    public LastSeenService(@Autowired LastSeenRepository lastSeenRepository,
                           @Value("${chatterbox.lastSeen.maxIdleTime:30}") long maxIdleTime) {
        this.lastSeenRepository = lastSeenRepository;
        this.maxIdleTime = maxIdleTime;
    }

    @Transactional
    public boolean update(long userId, OffsetDateTime timestamp) {
        boolean shouldWelcome = false;

        Optional<LastSeen> lastSeen = lastSeenRepository.findFirstByUserId(userId);
        if (lastSeen.isEmpty()) {
            log.debug("Creating new last seen entry for user {}", userId);
            lastSeenRepository.save(new LastSeen.Builder()
                    .setUserId(userId)
                    .setLastSeen(timestamp.toInstant())
                    .build());
            return false;
        }

        LastSeen seen = lastSeen.get();
        log.debug("Found last seen entry for user {}, comparing to configured maximum ({})", userId, maxIdleTime);
        long currentIdleDays = Duration.between(seen.getLastSeen(), timestamp).toDays();
        if (currentIdleDays >= maxIdleTime) {
            log.debug("User {} has been idle for {} days, will send welcome back message", userId, currentIdleDays);
            shouldWelcome = true;
        }

        log.debug("Updating last seen entry for user {}, idle time was {} days", userId, currentIdleDays);
        seen.setLastSeen(timestamp.toInstant());
        lastSeenRepository.save(seen);

        return shouldWelcome;
    }
}
