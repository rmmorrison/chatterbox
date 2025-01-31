package ca.ryanmorrison.chatterbox.listener;

import ca.ryanmorrison.chatterbox.extension.FormattedListenerAdapter;
import ca.ryanmorrison.chatterbox.service.LastSeenService;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LastSeenListener extends FormattedListenerAdapter {

    private final long maxIdleTime;

    private final LastSeenService lastSeenService;

    public LastSeenListener(@Value("${chatterbox.lastSeen.maxIdleTime:30}") long maxIdleTime,
                            @Autowired LastSeenService lastSeenService) {
        this.maxIdleTime = maxIdleTime;
        this.lastSeenService = lastSeenService;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.getAuthor().isSystem()) return;
        if (!event.isFromGuild()) return;

        if (lastSeenService.update(event.getAuthor().getIdLong(), event.getMessage().getTimeCreated())) {
            event.getChannel().sendMessageFormat("**A WILD %s HAS APPEARED, WELCOME BACK!**",
                    event.getAuthor().getAsMention(), maxIdleTime).queue();
        }
    }
}
