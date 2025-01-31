package ca.ryanmorrison.chatterbox.listener;

import ca.ryanmorrison.chatterbox.extension.FormattedListenerAdapter;
import ca.ryanmorrison.chatterbox.persistence.entity.LastSeen;
import ca.ryanmorrison.chatterbox.persistence.repository.LastSeenRepository;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

@Component
public class LastSeenListener extends FormattedListenerAdapter {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final long maxIdleTime;

    private final TransactionTemplate transactionTemplate;
    private final LastSeenRepository lastSeenRepository;

    public LastSeenListener(@Value("${chatterbox.lastSeen.maxIdleTime:30}") long maxIdleTime,
                            @Autowired PlatformTransactionManager platformTransactionManager,
                            @Autowired LastSeenRepository lastSeenRepository) {
        this.maxIdleTime = maxIdleTime;
        this.transactionTemplate = new TransactionTemplate(platformTransactionManager);
        this.lastSeenRepository = lastSeenRepository;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.getAuthor().isSystem()) return;
        if (!event.isFromGuild()) return;

        OffsetDateTime messageTime = event.getMessage().getTimeCreated();

        Boolean sendWelcome = transactionTemplate.execute(status -> {
            Optional<LastSeen> lastSeen = lastSeenRepository.findFirstByUserId(event.getAuthor().getIdLong());
            if (lastSeen.isEmpty()) {
                log.debug("Creating new last seen entry for user {}", event.getAuthor().getIdLong());
                lastSeenRepository.save(new LastSeen.Builder()
                        .setUserId(event.getAuthor().getIdLong())
                        .setLastSeen(Instant.now())
                        .build());
                return false;
            }

            LastSeen seen = lastSeen.get();
            boolean shouldWelcome = false;
            log.debug("Found last seen entry for user {}, comparing to configured maximum ({})", event.getAuthor().getIdLong(), maxIdleTime);
            long currentIdleDays = Duration.between(seen.getLastSeen(), messageTime).toDays();
            if (currentIdleDays >= maxIdleTime) {
                log.debug("User {} has been idle for {} days, will send welcome back message", event.getAuthor().getIdLong(), currentIdleDays);
                shouldWelcome = true;
            }

            log.debug("Updating last seen entry for user {}, idle time was {} days", event.getAuthor().getIdLong(), currentIdleDays);
            seen.setLastSeen(messageTime.toInstant());
            lastSeenRepository.save(seen);

            return shouldWelcome;
        });

        if (Boolean.TRUE.equals(sendWelcome)) {
            event.getChannel().sendMessageFormat("**A WILD %s HAS APPEARED, WELCOME BACK!**",
                    event.getAuthor().getAsMention(), maxIdleTime).queue();
        }
    }
}
