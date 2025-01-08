package ca.ryanmorrison.chatterbox.extension;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

public class Messages {

    public static ReplyCallbackAction sendSuccess(IReplyCallback callback, String successMessage) {
        return callback.reply("✅ " + successMessage);
    }

    public static WebhookMessageCreateAction<Message> sendSuccess(InteractionHook hook, String successMessage) {
        return hook.sendMessage("✅ " + successMessage);
    }

    public static ReplyCallbackAction sendError(IReplyCallback callback, String errorMessage) {
        return callback.reply("❌ " + errorMessage);
    }

    public static WebhookMessageCreateAction<Message> sendError(InteractionHook hook, String errorMessage) {
        return hook.sendMessage("❌ " + errorMessage);
    }
}
