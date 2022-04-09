package net.ryanmorrison.chatterbox.listener;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.ryanmorrison.chatterbox.service.ShoutService;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
public class ShoutListener extends ListenerAdapter {

    private final ShoutService shoutService;

    public ShoutListener(@Autowired ShoutService shoutService) {
        this.shoutService = shoutService;
    }

    @Override
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
