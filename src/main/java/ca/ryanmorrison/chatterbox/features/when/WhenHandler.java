package ca.ryanmorrison.chatterbox.features.when;

import ca.ryanmorrison.chatterbox.features.timezone.UserTimezonesRepository;
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
import java.util.Optional;

/**
 * Handles {@code /when at:<text> [in:<zone>] [private:<bool>]}.
 *
 * <p>Two zones at play:
 * <ul>
 *   <li>The <b>interpretation</b> zone — used to parse {@code at:}. The
 *       caller's stored timezone (from {@code /timezone set}) wins;
 *       {@code in:} is the fallback when none is stored.</li>
 *   <li>The <b>display</b> zone — used to render the wall-clock literal
 *       in the reply. The {@code in:} option wins; the caller's stored
 *       zone is the fallback; UTC is the last resort.</li>
 * </ul>
 * See {@link ZoneResolution}. The split is what makes
 * {@code /when at:tomorrow 12pm in:Asia/Kolkata} for a Toronto-stored
 * user mean <em>"my noon tomorrow, expressed in Kolkata's wall clock"</em>
 * rather than <em>"Kolkata noon tomorrow expressed in my wall clock"</em>.
 *
 * <p>The reply is a single line that reads as a sentence: viewer-locale
 * Discord timestamps first (which, for the caller, IS their wall clock —
 * {@code <t:UNIX:F>} auto-renders in viewer-local time), then the same
 * instant expressed in the display zone.
 */
final class WhenHandler extends ListenerAdapter {

    private final UserTimezonesRepository userTimezones;
    private final Clock clock;

    WhenHandler(UserTimezonesRepository userTimezones) {
        this(userTimezones, Clock.systemUTC());
    }

    /** Test seam — lets unit tests pin "now" with a fixed clock. */
    WhenHandler(UserTimezonesRepository userTimezones, Clock clock) {
        this.userTimezones = userTimezones;
        this.clock = clock;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!WhenModule.COMMAND.equals(event.getName())) return;

        String at        = stringOption(event, WhenModule.OPT_AT);
        String inRaw     = stringOption(event, WhenModule.OPT_IN);
        boolean ephemeral = booleanOption(event, WhenModule.OPT_PRIVATE);

        if (at == null || at.isBlank()) {
            event.reply("Give me a time to convert.").setEphemeral(true).queue();
            return;
        }

        // Validate in: if provided. An invalid string is a user error worth surfacing
        // explicitly rather than silently treating as "not provided".
        Optional<ZoneId> inOpt;
        if (inRaw == null || inRaw.isBlank()) {
            inOpt = Optional.empty();
        } else {
            inOpt = Timezones.resolve(inRaw);
            if (inOpt.isEmpty()) {
                event.reply("`" + inRaw + "` isn't a valid timezone. Pick one from the autocomplete, "
                                + "or pass an IANA id like `America/Toronto` or an offset like `+05:30`.")
                        .setEphemeral(true).queue();
                return;
            }
        }

        Optional<ZoneId> stored = userTimezones.find(event.getUser().getIdLong())
                .flatMap(Timezones::resolve);

        ZoneResolution.Resolved resolved = ZoneResolution.resolve(stored, inOpt);

        TimeParser.Result parsed = TimeParser.parse(at, resolved.interpretZone(), clock);
        switch (parsed) {
            case TimeParser.Result.Failed(String reason) ->
                    event.reply(reason).setEphemeral(true).queue();
            case TimeParser.Result.RequiresZone(String reason) ->
                    event.reply(reason).setEphemeral(true).queue();
            case TimeParser.Result.Ok(Instant instant) ->
                    event.reply(formatReply(resolved.displayZone(), instant))
                            .setEphemeral(ephemeral)
                            .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class))
                            .queue();
        }
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
     * Wall-clock rendering for the display zone. Pinned to {@link Locale#ENGLISH}
     * so the output looks the same regardless of where the bot is running.
     */
    private static final DateTimeFormatter LITERAL_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.ENGLISH);

    /**
     * Builds the user-facing reply.
     *
     * <p>When {@code displayZone} is present (caller passed {@code in:}),
     * the bold wall-clock literal in that zone leads — that's the answer
     * they explicitly asked about — followed by the Discord timestamp
     * markdown for the same instant (which Discord auto-localizes per
     * viewer). Leading with the literal avoids the earlier surprise where
     * the {@code <t:F>} appeared first in the output but rendered in the
     * viewer's locale, looking like a contradicting answer to the bold
     * line below it.
     *
     * <p>When {@code displayZone} is empty, the reply is just the Discord
     * timestamp markdown — there's no other zone to contrast against, so
     * nothing extra to render.
     *
     * <pre>
     *   no display: &lt;t:UNIX:F&gt; (&lt;t:UNIX:R&gt;)
     *   display set: **Sunday, May 10, 2026 at 9:00 AM** in Europe/London — &lt;t:UNIX:F&gt; (&lt;t:UNIX:R&gt;)
     * </pre>
     */
    static String formatReply(Optional<ZoneId> displayZone, Instant instant) {
        long epoch = instant.getEpochSecond();
        String discord = "<t:" + epoch + ":F> (<t:" + epoch + ":R>)";
        if (displayZone.isEmpty()) return discord;
        ZoneId zone = displayZone.get();
        String literal = LITERAL_FORMAT.format(instant.atZone(zone));
        return "**" + literal + "** in " + zone.getId() + " — " + discord;
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
