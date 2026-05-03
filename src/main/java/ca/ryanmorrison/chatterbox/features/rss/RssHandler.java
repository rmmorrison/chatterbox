package ca.ryanmorrison.chatterbox.features.rss;

import ca.ryanmorrison.chatterbox.common.permissions.Permissions;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Slash, select, and button glue for {@code /rss}. Replies are ephemeral.
 *
 * <p>Permission model:
 * <ul>
 *   <li>Anyone in a guild can {@code /rss add}.
 *   <li>{@code /rss remove} normally only lists feeds the caller added; users
 *       with {@link Permission#MESSAGE_MANAGE} in the channel see all feeds.
 * </ul>
 */
final class RssHandler extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(RssHandler.class);

    static final String CMD_NAME = "rss";

    static final int MIN_REFRESH_MINUTES = 15;
    static final int MAX_REFRESH_MINUTES = 24 * 60;
    static final int DEFAULT_REFRESH_MINUTES = 60;
    static final int MAX_FEEDS_PER_CHANNEL = 25;

    static final String OPT_URL     = "url";
    static final String OPT_REFRESH = "refresh_minutes";

    static final String SUB_ADD    = "add";
    static final String SUB_REMOVE = "remove";

    private static final String PREFIX           = CMD_NAME + ":";
    private static final String SELECT_REMOVE    = PREFIX + "remove:select";
    private static final String DELETE_CONFIRM   = PREFIX + "remove:confirm:";
    private static final String DELETE_CANCEL    = PREFIX + "remove:cancel:";

    private final RssRepository repo;
    private final RssFetcher fetcher;
    private final RssScheduler scheduler;
    private final PendingRssRemovals pending;

    RssHandler(RssRepository repo, RssFetcher fetcher, RssScheduler scheduler, PendingRssRemovals pending) {
        this.repo = repo;
        this.fetcher = fetcher;
        this.scheduler = scheduler;
        this.pending = pending;
    }

    // ---- slash entry ----

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!CMD_NAME.equals(event.getName())) return;
        if (!requireGuild(event)) return;
        String sub = event.getSubcommandName();
        if (sub == null) return;
        switch (sub) {
            case SUB_ADD    -> handleAdd(event);
            case SUB_REMOVE -> handleRemove(event);
            default         -> {}
        }
    }

    private void handleAdd(SlashCommandInteractionEvent event) {
        OptionMapping urlOpt = event.getOption(OPT_URL);
        if (urlOpt == null) {
            event.reply("URL is required.").setEphemeral(true).queue();
            return;
        }
        OptionMapping refreshOpt = event.getOption(OPT_REFRESH);
        int refreshMinutes = refreshOpt == null ? DEFAULT_REFRESH_MINUTES : (int) refreshOpt.getAsLong();
        if (refreshMinutes < MIN_REFRESH_MINUTES || refreshMinutes > MAX_REFRESH_MINUTES) {
            event.reply("Refresh interval must be between " + MIN_REFRESH_MINUTES
                            + " and " + MAX_REFRESH_MINUTES + " minutes.")
                    .setEphemeral(true).queue();
            return;
        }

        long channelId = event.getChannel().getIdLong();
        if (repo.countByChannel(channelId) >= MAX_FEEDS_PER_CHANNEL) {
            event.reply("This channel has reached the limit of " + MAX_FEEDS_PER_CHANNEL
                            + " RSS feeds. Remove one before adding another.")
                    .setEphemeral(true).queue();
            return;
        }

        // Network fetch + parse — defer so we don't time out the interaction.
        event.deferReply(true).queue();

        RssFetcher.Validated validated;
        try {
            validated = fetcher.validate(urlOpt.getAsString());
        } catch (RssFetcher.FetchException e) {
            event.getHook().sendMessage("Couldn't add that feed: " + e.getMessage())
                    .setEphemeral(true).queue();
            return;
        } catch (RuntimeException e) {
            log.error("Unexpected error validating RSS feed {}", urlOpt.getAsString(), e);
            event.getHook().sendMessage("Something went wrong validating that feed. Try again later.")
                    .setEphemeral(true).queue();
            return;
        }

        long guildId = event.getGuild().getIdLong();
        long userId = event.getUser().getIdLong();
        Optional<Feed> created = repo.insert(guildId, channelId, validated.url(), validated.title(),
                userId, refreshMinutes);
        if (created.isEmpty()) {
            event.getHook().sendMessage("That feed is already configured in this channel.")
                    .setEphemeral(true).queue();
            return;
        }

        scheduler.scheduleNew(created.get());
        event.getHook().sendMessage("Added **" + truncate(validated.title(), 200)
                        + "**. I'll check it every " + refreshMinutes + " minute"
                        + (refreshMinutes == 1 ? "" : "s") + ".")
                .setEphemeral(true).queue();
    }

    private void handleRemove(SlashCommandInteractionEvent event) {
        long channelId = event.getChannel().getIdLong();
        long userId = event.getUser().getIdLong();
        boolean isModerator = Permissions.canManageMessages(event);

        List<Feed> all = repo.listByChannel(channelId);
        List<Feed> visible = isModerator
                ? all
                : all.stream().filter(f -> f.addedBy() == userId).toList();

        if (visible.isEmpty()) {
            String msg = all.isEmpty()
                    ? "No RSS feeds are configured in this channel."
                    : "You haven't added any RSS feeds in this channel. (Moderators can remove any.)";
            event.reply(msg).setEphemeral(true).queue();
            return;
        }

        // Cap to 25 (Discord select limit) — we already cap feeds per channel at 25 so this
        // matters only for the moderator view in case the cap is ever raised.
        List<Feed> displayed = visible.size() <= 25 ? visible : visible.subList(0, 25);

        StringSelectMenu select = buildPicker(displayed);
        event.reply(displayed.size() < visible.size()
                        ? "Pick a feed to remove (showing the first " + displayed.size() + " of " + visible.size() + "):"
                        : "Pick a feed to remove:")
                .setComponents(ActionRow.of(select))
                .setEphemeral(true)
                .queue();
    }

    // ---- select handling ----

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith(PREFIX)) return;
        if (!requireGuild(event)) return;
        if (!SELECT_REMOVE.equals(id)) return;

        long feedId;
        try {
            feedId = Long.parseLong(event.getValues().get(0));
        } catch (RuntimeException e) {
            log.warn("Malformed remove-select value: {}", event.getValues());
            return;
        }
        Optional<Feed> opt = repo.findById(feedId);
        if (opt.isEmpty()) {
            event.editMessage("That feed no longer exists.").setComponents(List.of()).queue();
            return;
        }
        Feed feed = opt.get();
        if (!canRemove(event, feed)) {
            event.editMessage("You can only remove feeds you added. (Moderators can remove any.)")
                    .setComponents(List.of()).queue();
            return;
        }

        long channelId = event.getChannel().getIdLong();
        UUID token = pending.stash(feed.id(), channelId, event.getUser().getIdLong());
        event.editMessage("Remove **" + truncate(feed.title(), 200) + "**?\n"
                        + "URL: <" + truncate(feed.url(), 1000) + ">")
                .setComponents(ActionRow.of(
                        Button.danger(DELETE_CONFIRM + token, "Remove"),
                        Button.secondary(DELETE_CANCEL + token, "Cancel")))
                .queue();
    }

    // ---- button handling ----

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith(PREFIX)) return;
        if (!requireGuild(event)) return;

        if (id.startsWith(DELETE_CONFIRM)) {
            handleDeleteConfirm(event, id.substring(DELETE_CONFIRM.length()));
        } else if (id.startsWith(DELETE_CANCEL)) {
            UUID token = parseUuidOrNull(id.substring(DELETE_CANCEL.length()));
            if (token != null) pending.discard(token);
            event.editMessage("Cancelled.").setComponents(List.of()).queue();
        }
    }

    private void handleDeleteConfirm(ButtonInteractionEvent event, String tokenStr) {
        UUID token = parseUuidOrNull(tokenStr);
        if (token == null) {
            event.editMessage("Cancelled.").setComponents(List.of()).queue();
            return;
        }
        Optional<PendingRssRemovals.Pending> p = pending.consume(token);
        if (p.isEmpty()) {
            event.editMessage("This prompt expired. Run `/rss remove` again.")
                    .setComponents(List.of()).queue();
            return;
        }
        Optional<Feed> feedOpt = repo.findById(p.get().feedId());
        if (feedOpt.isEmpty()) {
            event.editMessage("That feed no longer exists.").setComponents(List.of()).queue();
            return;
        }
        Feed feed = feedOpt.get();
        if (!canRemove(event, feed)) {
            event.editMessage("You can only remove feeds you added. (Moderators can remove any.)")
                    .setComponents(List.of()).queue();
            return;
        }

        int rows = repo.deleteById(feed.id());
        if (rows == 0) {
            event.editMessage("That feed no longer exists.").setComponents(List.of()).queue();
            return;
        }
        scheduler.cancel(feed.id());
        event.editMessage("Removed **" + truncate(feed.title(), 200) + "**.")
                .setComponents(List.of()).queue();
    }

    // ---- helpers ----

    private static StringSelectMenu buildPicker(List<Feed> feeds) {
        var b = StringSelectMenu.create(SELECT_REMOVE).setPlaceholder("Pick a feed");
        for (Feed f : feeds) {
            b.addOptions(SelectOption.of(
                            truncate(f.title(), 100),
                            String.valueOf(f.id()))
                    .withDescription(truncate(f.url(), 100)));
        }
        return b.build();
    }

    private static boolean requireGuild(IReplyCallback event) {
        if (event.getGuild() == null) {
            event.reply("This command is only available in servers.").setEphemeral(true).queue();
            return false;
        }
        return true;
    }

    private static boolean canRemove(IReplyCallback event, Feed feed) {
        if (Permissions.canManageMessages(event)) return true;
        Member m = event.getMember();
        return m != null && m.getIdLong() == feed.addedBy();
    }

    private static UUID parseUuidOrNull(String token) {
        try { return UUID.fromString(token); }
        catch (IllegalArgumentException e) { return null; }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
