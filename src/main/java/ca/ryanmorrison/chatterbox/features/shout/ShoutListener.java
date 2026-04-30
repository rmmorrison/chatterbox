package ca.ryanmorrison.chatterbox.features.shout;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

final class ShoutListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ShoutListener.class);

    private final ShoutDetector detector;
    private final ShoutRepository repo;

    ShoutListener(ShoutDetector detector, ShoutRepository repo) {
        this.detector = detector;
        this.repo = repo;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild()) return;
        if (isBot(event.getAuthor())) return;

        String content = event.getMessage().getContentRaw();
        if (!detector.isShouting(content)) return;

        long channelId = event.getChannel().getIdLong();
        long messageId = event.getMessageIdLong();

        try {
            repo.tryInsert(channelId, messageId, content);
        } catch (RuntimeException e) {
            log.warn("Failed to persist shout for channel {} message {}", channelId, messageId, e);
        }

        repo.randomPeer(channelId, messageId).ifPresent(reply ->
                event.getChannel().sendMessage(reply).queue(
                        ok -> {},
                        err -> log.warn("Failed to send shout reply: {}", err.toString())));
    }

    @Override
    public void onMessageUpdate(MessageUpdateEvent event) {
        if (!event.isFromGuild()) return;
        if (isBot(event.getAuthor())) return;

        long messageId = event.getMessageIdLong();
        Optional<String> stored = repo.findContentByMessageId(messageId);
        if (stored.isEmpty()) return; // no retroactive upgrades

        String newContent = event.getMessage().getContentRaw();
        long channelId = event.getChannel().getIdLong();

        if (!detector.isShouting(newContent)) {
            repo.deleteByMessageId(messageId);
            return;
        }
        if (stored.get().equals(newContent)) return; // no-op edit (e.g. embed re-render)

        repo.updateOrDeleteOnCollision(channelId, messageId, newContent);
    }

    @Override
    public void onMessageDelete(MessageDeleteEvent event) {
        if (!event.isFromGuild()) return;
        repo.deleteByMessageId(event.getMessageIdLong());
    }

    @Override
    public void onMessageBulkDelete(MessageBulkDeleteEvent event) {
        List<Long> ids = event.getMessageIds().stream().map(Long::parseLong).toList();
        repo.deleteByMessageIds(ids);
    }

    private static boolean isBot(User author) {
        return author == null || author.isBot();
    }
}
