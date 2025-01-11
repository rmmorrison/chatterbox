package ca.ryanmorrison.chatterbox.extension;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class FormattedListenerAdapter extends ListenerAdapter {

    protected MessageEmbed buildSuccessResponse(String message) {
        return buildResponseEmbed(ResponseType.SUCCESS, message);
    }

    protected MessageEmbed buildErrorResponse(String message) {
        return buildResponseEmbed(ResponseType.ERROR, message);
    }

    private MessageEmbed buildResponseEmbed(ResponseType responseType, String message) {
        return new EmbedBuilder()
                .setTitle(responseType.title)
                .setDescription(message)
                .setColor(responseType.colour)
                .build();
    }

    protected enum ResponseType {
        SUCCESS("✅ Success", 0x00FF00),
        ERROR("❌ Error", 0xFF0000);

        private final String title;
        private final int colour;

        ResponseType(String title, int colour) {
            this.title = title;
            this.colour = colour;
        }
    }
}
