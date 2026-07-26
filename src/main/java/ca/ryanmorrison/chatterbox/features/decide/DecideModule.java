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
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
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

    /** Discord rejects message bodies over this; exceeding it throws on reply. */
    static final int MAX_MESSAGE_LENGTH = 2000;

    /**
     * Ceiling on the options string. Without it Discord's own 6000-char default
     * applies, and a single whitespace-free option that long produced a reply
     * of ~6009 chars: {@code options.size() == 1} skips the truncated "from"
     * line entirely, so nothing else bounded the output.
     */
    static final int MAX_OPTIONS_LENGTH = 1000;

    @Override public String name() { return "decide"; }

    @Override
    public List<SlashCommandData> slashCommands(InitContext ctx) {
        return List.of(Commands.slash(COMMAND, "Pick one of several options at random.")
                .addOptions(new OptionData(OptionType.STRING, OPT_OPTIONS,
                                "Options separated by whitespace, or use the word \"or\" between them.", true)
                                .setMaxLength(MAX_OPTIONS_LENGTH),
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
        // Backstop on the rendered result, not just the input: the option cap
        // bounds what a user can type, but this is what Discord actually
        // measures, and it throws rather than truncating.
        return truncate(sb.toString(), MAX_MESSAGE_LENGTH);
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) return s;
        // Never split a surrogate pair — emoji in an option would otherwise
        // leave a lone surrogate and render as a replacement character.
        int end = max - 1;
        if (Character.isHighSurrogate(s.charAt(end - 1))) end--;
        return s.substring(0, end) + "…";
    }
}
