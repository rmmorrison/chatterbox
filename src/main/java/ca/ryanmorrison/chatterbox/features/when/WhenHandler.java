package ca.ryanmorrison.chatterbox.features.when;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * Handles {@code /when at:<text> in:<zone> [private:<bool>]}.
 *
 * <p>Parses {@code at:} via {@link TimeParser} in the timezone resolved
 * from {@code in:}, then renders the resolved instant as Discord's
 * {@code <t:UNIX:STYLE>} markdown — once in long form ({@code F}) and
 * once relative ({@code R}) — so every viewer sees both the absolute
 * time in their local zone and how far away it is.
 *
 * <p>Public reply by default; the whole point is to share. {@code private:true}
 * keeps the result ephemeral for users who want to preview the parse before
 * sharing. Mentions in either input field are suppressed.
 */
final class WhenHandler extends ListenerAdapter {

    private final Clock clock;

    WhenHandler() {
        this(Clock.systemUTC());
    }

    /** Test seam — lets unit tests pin "now" with a fixed clock. */
    WhenHandler(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!WhenModule.COMMAND.equals(event.getName())) return;

        String at   = stringOption(event, WhenModule.OPT_AT);
        String zone = stringOption(event, WhenModule.OPT_IN);
        boolean ephemeral = booleanOption(event, WhenModule.OPT_PRIVATE);

        if (at == null || at.isBlank()) {
            event.reply("Give me a time to convert.").setEphemeral(true).queue();
            return;
        }

        var resolvedZone = Timezones.resolve(zone);
        if (resolvedZone.isEmpty()) {
            event.reply("`" + zone + "` isn't a valid timezone. Pick one from the autocomplete, "
                            + "or pass an IANA id like `America/Toronto` or an offset like `+05:30`.")
                    .setEphemeral(true).queue();
            return;
        }

        TimeParser.Result parsed = TimeParser.parse(at, resolvedZone.get(), clock);
        if (parsed instanceof TimeParser.Result.Failed(String reason)) {
            event.reply(reason).setEphemeral(true).queue();
            return;
        }

        Instant instant = ((TimeParser.Result.Ok) parsed).instant();
        event.reply(formatReply(resolvedZone.get(), instant))
                .setEphemeral(ephemeral)
                .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class))
                .queue();
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!WhenModule.COMMAND.equals(event.getName())) return;
        if (!WhenModule.OPT_IN.equals(event.getFocusedOption().getName())) return;

        List<Command.Choice> choices = Timezones.suggest(event.getFocusedOption().getValue()).stream()
                .map(z -> new Command.Choice(z, z))
                .toList();
        event.replyChoices(choices).queue();
    }

    /**
     * Wall-clock rendering for the requested zone. Pinned to {@link Locale#ENGLISH}
     * so the output looks the same regardless of where the bot is running.
     */
    private static final DateTimeFormatter LITERAL_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.ENGLISH);

    /**
     * Builds the user-facing reply as a single line:
     * <pre>
     *   &lt;t:UNIX:F&gt; (&lt;t:UNIX:R&gt;) in Asia/Kolkata → **Saturday, May 9, 2026 at 8:00 AM**
     * </pre>
     * The Discord timestamps come first because they auto-render in the
     * viewer's locale (so for the caller they're effectively "your time"),
     * and the bold trailing wall-clock is the same moment expressed in
     * the requested zone. Reading left-to-right works as a sentence:
     * "this moment [your view] in &lt;zone&gt; is [their wall clock]." That
     * eliminates the ambiguity of the earlier two-clock layout where the
     * caller had to figure out which clock was which.
     */
    static String formatReply(ZoneId zone, Instant instant) {
        long epoch = instant.getEpochSecond();
        String literal = LITERAL_FORMAT.format(instant.atZone(zone));
        return "<t:" + epoch + ":F> (<t:" + epoch + ":R>) in "
                + zone.getId() + " → **" + literal + "**";
    }

    private static String stringOption(SlashCommandInteractionEvent event, String name) {
        OptionMapping opt = event.getOption(name);
        return opt == null ? null : opt.getAsString();
    }

    private static boolean booleanOption(SlashCommandInteractionEvent event, String name) {
        OptionMapping opt = event.getOption(name);
        return opt != null && opt.getAsBoolean();
    }
}
