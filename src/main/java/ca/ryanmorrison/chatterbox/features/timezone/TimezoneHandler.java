package ca.ryanmorrison.chatterbox.features.timezone;

import ca.ryanmorrison.chatterbox.features.when.Timezones;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * {@code /timezone set tz:<zone>}, {@code /timezone show}, {@code /timezone clear}.
 *
 * <p>Stores a single IANA zone string per user, used by features that need
 * a "calendar reference" for a Discord user — currently only {@code /when}'s
 * relative-day parsing ("tomorrow", "today", weekday names, bare times),
 * which is otherwise ambiguous because Discord doesn't expose user
 * timezones to bots.
 *
 * <p>Replies are ephemeral throughout — no need to clutter the channel
 * with someone's timezone preference.
 */
final class TimezoneHandler extends ListenerAdapter {

    static final String CMD_NAME  = "timezone";
    static final String SUB_SET   = "set";
    static final String SUB_SHOW  = "show";
    static final String SUB_CLEAR = "clear";
    static final String OPT_TZ    = "tz";

    private static final int MAX_AUTOCOMPLETE_CHOICES = 25;

    private final UserTimezonesRepository repo;
    private final Clock clock;

    TimezoneHandler(UserTimezonesRepository repo) {
        this(repo, Clock.systemUTC());
    }

    /** Test seam — pin "now" for deterministic updated_at values. */
    TimezoneHandler(UserTimezonesRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!CMD_NAME.equals(event.getName())) return;

        String sub = event.getSubcommandName();
        if (sub == null) return;
        long userId = event.getUser().getIdLong();
        switch (sub) {
            case SUB_SET   -> handleSet(event, userId);
            case SUB_SHOW  -> handleShow(event, userId);
            case SUB_CLEAR -> handleClear(event, userId);
            default        -> {}
        }
    }

    private void handleSet(SlashCommandInteractionEvent event, long userId) {
        OptionMapping tzOpt = event.getOption(OPT_TZ);
        String raw = tzOpt == null ? null : tzOpt.getAsString();
        Optional<java.time.ZoneId> resolved = Timezones.resolve(raw);
        if (resolved.isEmpty()) {
            event.reply("`" + raw + "` isn't a valid timezone. Pick one from the autocomplete, "
                            + "or pass an IANA id like `America/Toronto` or an offset like `+05:30`.")
                    .setEphemeral(true).queue();
            return;
        }
        String zoneId = resolved.get().getId();
        repo.put(userId, zoneId, OffsetDateTime.now(clock));
        event.reply("Your timezone is now **" + zoneId + "**. "
                        + "Commands like `/when` will use this for relative-day parsing.")
                .setEphemeral(true).queue();
    }

    private void handleShow(SlashCommandInteractionEvent event, long userId) {
        Optional<String> existing = repo.find(userId);
        if (existing.isEmpty()) {
            event.reply("You haven't set a timezone yet. Use `/timezone set tz:<zone>` to set one.")
                    .setEphemeral(true).queue();
            return;
        }
        event.reply("Your timezone is **" + existing.get() + "**.").setEphemeral(true).queue();
    }

    private void handleClear(SlashCommandInteractionEvent event, long userId) {
        boolean removed = repo.delete(userId);
        if (!removed) {
            event.reply("You didn't have a timezone set.").setEphemeral(true).queue();
            return;
        }
        event.reply("Cleared. `/when` will reject relative-day inputs (`tomorrow`, `friday`, etc.) "
                        + "until you set one again.")
                .setEphemeral(true).queue();
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!CMD_NAME.equals(event.getName())) return;
        if (!OPT_TZ.equals(event.getFocusedOption().getName())) return;

        List<Command.Choice> choices = Timezones.suggest(event.getFocusedOption().getValue()).stream()
                .limit(MAX_AUTOCOMPLETE_CHOICES)
                .map(z -> new Command.Choice(z, z))
                .toList();
        event.replyChoices(choices).queue();
    }
}
