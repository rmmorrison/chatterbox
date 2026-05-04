package ca.ryanmorrison.chatterbox.features.format;

import ca.ryanmorrison.chatterbox.module.InitContext;
import ca.ryanmorrison.chatterbox.module.Module;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.EnumSet;
import java.util.List;

/**
 * {@code /format style:<choice> text:<text> [private:<bool>]} — apply a
 * fun text transform and post the result. Public by default; pass
 * {@code private:true} to keep it ephemeral.
 *
 * <p>Mentions in the input text are suppressed so a hostile
 * {@code /format text:"@everyone hi"} can't actually ping anyone.
 */
public final class FormatModule extends ListenerAdapter implements Module {

    static final String COMMAND     = "format";
    static final String OPT_STYLE   = "style";
    static final String OPT_TEXT    = "text";
    static final String OPT_PRIVATE = "private";

    /** Discord caps message bodies at 2000 chars; cap the input to leave headroom. */
    static final int MAX_INPUT_LENGTH = 1500;

    @Override public String name() { return "format"; }

    @Override
    public List<SlashCommandData> slashCommands(InitContext ctx) {
        OptionData style = new OptionData(OptionType.STRING, OPT_STYLE,
                "How to transform the text.", true);
        for (TextStyle s : TextStyle.values()) {
            style.addChoice(s.label(), s.value());
        }
        return List.of(Commands.slash(COMMAND, "Transform some text in a fun way.")
                .addOptions(
                        style,
                        new OptionData(OptionType.STRING, OPT_TEXT,
                                "The text to format.", true),
                        new OptionData(OptionType.BOOLEAN, OPT_PRIVATE,
                                "Show the result only to you instead of the channel.", false)));
    }

    @Override
    public List<EventListener> listeners(InitContext ctx) {
        return List.of(this);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!COMMAND.equals(event.getName())) return;

        OptionMapping styleOption = event.getOption(OPT_STYLE);
        OptionMapping textOption  = event.getOption(OPT_TEXT);
        if (styleOption == null || textOption == null) {
            event.reply("Pick a style and give me some text.").setEphemeral(true).queue();
            return;
        }

        TextStyle style;
        try {
            style = TextStyle.fromValue(styleOption.getAsString());
        } catch (IllegalArgumentException e) {
            event.reply("Unknown style.").setEphemeral(true).queue();
            return;
        }

        String text = textOption.getAsString();
        if (text.length() > MAX_INPUT_LENGTH) {
            text = text.substring(0, MAX_INPUT_LENGTH);
        }
        String formatted = style.apply(text);
        if (formatted.isEmpty()) {
            event.reply("Give me something with at least one character to work with.")
                    .setEphemeral(true).queue();
            return;
        }

        OptionMapping privateOption = event.getOption(OPT_PRIVATE);
        boolean ephemeral = privateOption != null && privateOption.getAsBoolean();

        event.reply(formatted)
                .setEphemeral(ephemeral)
                .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class))
                .queue();
    }
}
