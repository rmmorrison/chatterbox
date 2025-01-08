package ca.ryanmorrison.chatterbox.listener;

import ca.ryanmorrison.chatterbox.service.TriggerService;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TriggerListener extends ListenerAdapter {

    private final TriggerService triggerService;

    public TriggerListener(@Autowired TriggerService triggerService) {
        this.triggerService = triggerService;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.getAuthor().isSystem()) return;
        if (!event.isFromGuild()) return;

        triggerService.getExpressions(event.getChannel().getIdLong()).entrySet().stream()
                .filter(entry -> entry.getKey().matcher(event.getMessage().getContentDisplay()).matches())
                .findFirst()
                .ifPresent(entry -> event.getMessage().reply(entry.getValue()).queue());
    }
}
