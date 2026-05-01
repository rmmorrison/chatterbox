package ca.ryanmorrison.chatterbox.features.shout;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

final class ShoutListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ShoutListener.class);

    private final ShoutDetector detector;
    private final ShoutRepository shouts;
    private final ShoutHistoryRepository history;

    ShoutListener(ShoutDetector detector, ShoutRepository shouts, ShoutHistoryRepository history) {
        this.detector = detector;
        this.shouts = shouts;
        this.history = history;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild()) return;
        if (isBot(event.getAuthor())) return;

        String content = event.getMessage().getContentRaw();
        if (!detector.isShouting(content)) return;

        long channelId = event.getChannel().getIdLong();
        long messageId = event.getMessageIdLong();
        long authorId = event.getAuthor().getIdLong();
        OffsetDateTime authoredAt = event.getMessage().getTimeCreated();

        try {
            shouts.tryInsert(channelId, messageId, content, authorId, authoredAt);
        } catch (RuntimeException e) {
            log.warn("Failed to persist shout for channel {} message {}", channelId, messageId, e);
        }

        shouts.randomPeer(channelId, messageId).ifPresent(peer ->
                event.getChannel().sendMessage(peer.content()).queue(
                        sent -> recordHistory(channelId, peer.shoutId()),
                        err -> log.warn("Failed to send shout reply: {}", err.toString())));
    }

    @Override
    public void onMessageUpdate(MessageUpdateEvent event) {
        if (!event.isFromGuild()) return;
        if (isBot(event.getAuthor())) return;

        long messageId = event.getMessageIdLong();
        Optional<String> stored = shouts.findContentByMessageId(messageId);
        if (stored.isEmpty()) return;

        String newContent = event.getMessage().getContentRaw();
        long channelId = event.getChannel().getIdLong();

        if (!detector.isShouting(newContent)) {
            shouts.deleteByMessageId(messageId);
            return;
        }
        if (stored.get().equals(newContent)) return;

        shouts.updateOrDeleteOnCollision(channelId, messageId, newContent);
    }

    @Override
    public void onMessageDelete(MessageDeleteEvent event) {
        if (!event.isFromGuild()) return;
        shouts.deleteByMessageId(event.getMessageIdLong());
    }

    @Override
    public void onMessageBulkDelete(MessageBulkDeleteEvent event) {
        List<Long> ids = event.getMessageIds().stream().map(Long::parseLong).toList();
        shouts.deleteByMessageIds(ids);
    }

    private void recordHistory(long channelId, long shoutId) {
        try {
            history.record(channelId, shoutId);
        } catch (RuntimeException e) {
            // The shout might have been deleted between SELECT and our success callback,
            // failing the FK. That's OK — users still saw the message; history just lacks an entry.
            log.warn("Failed to record shout history for channel {} shout {}: {}",
                    channelId, shoutId, e.toString());
        }
    }

    private static boolean isBot(User author) {
        return author == null || author.isBot();
    }
}
