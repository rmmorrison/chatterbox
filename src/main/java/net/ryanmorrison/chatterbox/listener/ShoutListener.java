package net.ryanmorrison.chatterbox.listener;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.ryanmorrison.chatterbox.service.ShoutService;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.transaction.Transactional;
import java.util.Optional;

@Component
@Slf4j
public class ShoutListener extends ListenerAdapter {

    private final ShoutService shoutService;

    public ShoutListener(@Autowired ShoutService shoutService) {
        this.shoutService = shoutService;
    }

    @Override
    @Transactional
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (contentIsNotShout(event.getAuthor(), event.getMessage().getContentDisplay())) {
            log.debug("Received message \"{}\" in channel {} which does not match shout, ignoring.",
                    event.getMessage().getContentDisplay(), event.getChannel().getIdLong());
            return;
        }

        Optional<Message> randomMessage = shoutService.save(event.getMessage());
        randomMessage.ifPresent(message -> event.getChannel().sendMessage(
                new MessageBuilder()
                        .append(message.getContentDisplay(), MessageBuilder.Formatting.BOLD)
                        .build()).queue());
    }

    @Override
    @Transactional
    public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
        if (contentIsNotShout(event.getAuthor(), event.getMessage().getContentDisplay())) {
            log.debug("Received updated message \"{}\" in channel {} which does not match shout, " +
                            "deleting original shout if it exists.",
                    event.getMessage().getContentDisplay(), event.getChannel().getIdLong());

            shoutService.delete(event.getMessage());
            return;
        }

        log.debug("Received updated message \"{}\" in channel {} which matches shout, calling update if it exists.",
                event.getMessage().getContentDisplay(), event.getChannel().getIdLong());
        this.shoutService.update(event.getMessage());
    }

    @Override
    @Transactional
    public void onMessageDelete(@NotNull MessageDeleteEvent event) {
        log.debug("Received deleted message with ID {} in channel {}, deleting corresponding shout if it exists.",
                event.getMessageIdLong(), event.getChannel().getIdLong());
        shoutService.delete(event.getMessageIdLong());
    }

    private boolean contentIsNotShout(User author, String content) {
        if (author.isBot()) return true;
        if (!content.equals(content.toUpperCase())) return true;
        if (content.startsWith("$")) return true; // special case for currency
        if (content.startsWith("http")) return true; // special case for links
        if (content.length() <= 5) return true;

        int nonCharCount = 0;
        for (char current : content.toCharArray()) {
            if (!Character.isLetter(current)) {
                nonCharCount++;
            }
        }

        return (int) (((float) nonCharCount / content.length()) * 100) > 50;
    }
}
