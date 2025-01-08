package ca.ryanmorrison.chatterbox.extension;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;

public class Messages {

    public static WebhookMessageCreateAction<Message> sendError(InteractionHook hook, String errorMessage) {
        return hook.sendMessage("❌ " + errorMessage);
    }
}
