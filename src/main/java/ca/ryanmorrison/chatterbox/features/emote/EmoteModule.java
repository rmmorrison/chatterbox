package ca.ryanmorrison.chatterbox.features.emote;

import ca.ryanmorrison.chatterbox.module.InitContext;
import ca.ryanmorrison.chatterbox.module.Module;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

/**
 * {@code /emote name:<choice> [private:<bool>]} — post one of a small roster
 * of canned text emotes (flipped tables, shrugs, etc.). Public by default;
 * pass {@code private:true} to keep it ephemeral.
 */
public final class EmoteModule extends ListenerAdapter implements Module {

    static final String COMMAND     = "emote";
    static final String OPT_NAME    = "name";
    static final String OPT_PRIVATE = "private";

    @Override public String name() { return "emote"; }

    @Override
    public List<SlashCommandData> slashCommands(InitContext ctx) {
        OptionData name = new OptionData(OptionType.STRING, OPT_NAME,
                "Which emote to post.", true);
        for (Emote e : Emote.values()) {
            name.addChoice(e.label(), e.value());
        }
        return List.of(Commands.slash(COMMAND, "Post a canned text emote.")
                .addOptions(
                        name,
                        new OptionData(OptionType.BOOLEAN, OPT_PRIVATE,
                                "Show the emote only to you instead of the channel.", false)));
    }

    @Override
    public List<EventListener> listeners(InitContext ctx) {
        return List.of(this);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!COMMAND.equals(event.getName())) return;

        OptionMapping nameOption = event.getOption(OPT_NAME);
        if (nameOption == null) return;

        Emote emote;
        try {
            emote = Emote.fromValue(nameOption.getAsString());
        } catch (IllegalArgumentException e) {
            event.reply("Unknown emote.").setEphemeral(true).queue();
            return;
        }

        OptionMapping privateOption = event.getOption(OPT_PRIVATE);
        boolean ephemeral = privateOption != null && privateOption.getAsBoolean();

        event.reply(emote.text()).setEphemeral(ephemeral).queue();
    }
}
