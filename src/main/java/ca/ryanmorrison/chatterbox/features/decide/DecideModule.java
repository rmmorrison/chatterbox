package ca.ryanmorrison.chatterbox.features.decide;

import ca.ryanmorrison.chatterbox.module.InitContext;
import ca.ryanmorrison.chatterbox.module.Module;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * {@code /decide} — pick a random option from a free-form list. Public by
 * default since decisions are usually shared; pass {@code private:true} to
 * keep the result to yourself.
 *
 * <p>Splits via {@link DecideOptionsParser} so users can phrase choices
 * either as {@code "pizza tacos sushi"} (whitespace) or
 * {@code "say hello or be silent"} (the word "or").
 *
 * <p>Mentions in the rendered options are suppressed so a malicious
 * {@code /decide options:"@everyone or nope"} can't ping the channel.
 */
public final class DecideModule extends ListenerAdapter implements Module {

    static final String COMMAND  = "decide";
    static final String OPT_OPTIONS = "options";
    static final String OPT_PRIVATE = "private";

    private static final int MAX_FROM_LINE_LENGTH = 1500;

    @Override public String name() { return "decide"; }

    @Override
    public List<SlashCommandData> slashCommands(InitContext ctx) {
        return List.of(Commands.slash(COMMAND, "Pick one of several options at random.")
                .addOption(OptionType.STRING, OPT_OPTIONS,
                        "Options separated by whitespace, or use the word \"or\" between them.", true)
                .addOption(OptionType.BOOLEAN, OPT_PRIVATE,
                        "Show the result only to you instead of the channel.", false));
    }

    @Override
    public List<EventListener> listeners(InitContext ctx) {
        return List.of(this);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!COMMAND.equals(event.getName())) return;

        OptionMapping optionsOption = event.getOption(OPT_OPTIONS);
        String raw = optionsOption == null ? null : optionsOption.getAsString();
        List<String> options = DecideOptionsParser.parse(raw);

        OptionMapping privateOption = event.getOption(OPT_PRIVATE);
        boolean ephemeral = privateOption != null && privateOption.getAsBoolean();

        if (options.isEmpty()) {
            event.reply("Give me at least one option to choose from. "
                    + "Separate them with whitespace, or use the word `or` between them.")
                    .setEphemeral(true).queue();
            return;
        }

        String pick = options.get(ThreadLocalRandom.current().nextInt(options.size()));
        String body = renderResult(pick, options);

        event.reply(body)
                .setEphemeral(ephemeral)
                .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class))
                .queue();
    }

    static String renderResult(String pick, List<String> options) {
        StringBuilder sb = new StringBuilder();
        sb.append("🎲 **").append(pick).append("**");
        if (options.size() > 1) {
            String joined = String.join(" · ", options);
            if (joined.length() > MAX_FROM_LINE_LENGTH) {
                joined = joined.substring(0, MAX_FROM_LINE_LENGTH - 1) + "…";
            }
            sb.append("\n_(from: ").append(joined).append(")_");
        }
        return sb.toString();
    }
}
