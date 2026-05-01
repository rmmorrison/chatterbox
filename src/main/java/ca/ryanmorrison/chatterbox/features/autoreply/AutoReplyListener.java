package ca.ryanmorrison.chatterbox.features.autoreply;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Watches incoming guild messages and dispatches the configured response from
 * the first matching auto-reply rule for that channel. Bot messages and DMs
 * are ignored.
 */
final class AutoReplyListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(AutoReplyListener.class);

    private final AutoReplyMatcher matcher;

    AutoReplyListener(AutoReplyMatcher matcher) {
        this.matcher = matcher;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild()) return;
        if (isBot(event.getAuthor())) return;

        long channelId = event.getChannel().getIdLong();
        String content = event.getMessage().getContentRaw();

        matcher.firstMatch(channelId, content).ifPresent(response ->
                event.getChannel().sendMessage(response).queue(
                        ok -> {},
                        err -> log.warn("Failed to send auto-reply in channel {}: {}", channelId, err.toString())));
    }

    private static boolean isBot(User author) {
        return author == null || author.isBot();
    }
}
