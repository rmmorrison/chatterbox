package ca.ryanmorrison.chatterbox.features.shout;

import ca.ryanmorrison.chatterbox.common.permissions.Permissions;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Slash command and button glue for {@code /shout-history}. Looks up the
 * latest history entry on slash invocation, then steps older / newer / soft-
 * deletes / restores in response to button clicks. Replies are ephemeral, so
 * cross-user button spoofing isn't a concern.
 *
 * <p>Moderator privileges are gated on {@code MESSAGE_MANAGE} in the
 * specific channel — checked on every slash and button event so a user who
 * loses the permission mid-pagination loses the buttons immediately. A
 * non-moderator who somehow clicks a {@code delete} or {@code restore}
 * button (e.g. role removed between renders) is silently no-op'd.
 */
final class ShoutHistoryHandler extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ShoutHistoryHandler.class);
    private static final String UNKNOWN_MEMBER = "Former member";

    private final ShoutRepository shouts;
    private final ShoutHistoryRepository history;

    ShoutHistoryHandler(ShoutRepository shouts, ShoutHistoryRepository history) {
        this.shouts = shouts;
        this.history = history;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!ShoutHistoryView.CMD_NAME.equals(event.getName())) return;

        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) {
            event.reply("This command is only available in servers.").setEphemeral(true).queue();
            return;
        }

        long channelId = event.getChannel().getIdLong();
        boolean canModerate = canModerate(member, event.getGuildChannel());

        Optional<HistoryEntry> latest = history.findLatest(channelId, canModerate);
        if (latest.isEmpty()) {
            event.reply("No shout history for this channel yet.").setEphemeral(true).queue();
            return;
        }
        renderReply(event, guild, channelId, latest.get(), canModerate);
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith(ShoutHistoryView.BUTTON_PREFIX)) return;

        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) return;

        long channelId = event.getChannel().getIdLong();
        boolean canModerate = canModerate(member, event.getGuildChannel());

        long historyId;
        try {
            historyId = parseCursor(id);
        } catch (NumberFormatException e) {
            log.warn("Malformed shout-history button id: {}", id);
            return;
        }

        if (id.startsWith(ShoutHistoryView.DELETE) || id.startsWith(ShoutHistoryView.RESTORE)) {
            if (!canModerate) return; // race: lost the permission since the buttons rendered
            applyModeration(event, guild, channelId, historyId, member, id);
            return;
        }

        Optional<HistoryEntry> next;
        if (id.startsWith(ShoutHistoryView.OLDER)) {
            next = history.findOlder(channelId, historyId, canModerate);
        } else if (id.startsWith(ShoutHistoryView.NEWER)) {
            next = history.findNewer(channelId, historyId, canModerate);
        } else {
            return;
        }

        if (next.isEmpty()) {
            // Neighbour vanished (cascade from a hard-delete since render). Snap back to
            // the current entry, or to whatever's still there for this viewer.
            next = history.findById(channelId, historyId, canModerate)
                    .or(() -> history.findLatest(channelId, canModerate));
        }
        if (next.isEmpty()) {
            event.editMessage("No shout history for this channel anymore.")
                    .setEmbeds(List.of())
                    .setComponents(List.of())
                    .queue();
            return;
        }

        renderEdit(event, guild, channelId, next.get(), canModerate);
    }

    private void applyModeration(ButtonInteractionEvent event, Guild guild, long channelId,
                                 long historyId, Member viewer, String buttonId) {
        // Resolve current state to find the underlying shout id.
        Optional<HistoryEntry> current = history.findById(channelId, historyId, true);
        if (current.isEmpty()) {
            event.editMessage("That shout is no longer available.")
                    .setEmbeds(List.of())
                    .setComponents(List.of())
                    .queue();
            return;
        }
        long shoutId = current.get().shoutId();

        if (buttonId.startsWith(ShoutHistoryView.DELETE)) {
            shouts.softDelete(shoutId, viewer.getIdLong());
        } else {
            shouts.restore(shoutId);
        }

        Optional<HistoryEntry> updated = history.findById(channelId, historyId, true);
        if (updated.isEmpty()) {
            event.editMessage("That shout is no longer available.")
                    .setEmbeds(List.of())
                    .setComponents(List.of())
                    .queue();
            return;
        }
        renderEdit(event, guild, channelId, updated.get(), true);
    }

    private void renderReply(SlashCommandInteractionEvent event, Guild guild, long channelId,
                             HistoryEntry entry, boolean canModerate) {
        var older = history.findOlder(channelId, entry.historyId(), canModerate);
        var newer = history.findNewer(channelId, entry.historyId(), canModerate);
        var pos = history.position(channelId, entry.historyId(), canModerate);

        buildEmbed(guild, channelId, entry, pos).thenAccept(embed ->
                event.replyEmbeds(embed)
                        .setEphemeral(true)
                        .setComponents(ShoutHistoryView.components(entry, older, newer, canModerate))
                        .queue());
    }

    private void renderEdit(ButtonInteractionEvent event, Guild guild, long channelId,
                            HistoryEntry entry, boolean canModerate) {
        var older = history.findOlder(channelId, entry.historyId(), canModerate);
        var newer = history.findNewer(channelId, entry.historyId(), canModerate);
        var pos = history.position(channelId, entry.historyId(), canModerate);

        buildEmbed(guild, channelId, entry, pos).thenAccept(embed ->
                event.editMessageEmbeds(embed)
                        .setComponents(ShoutHistoryView.components(entry, older, newer, canModerate))
                        .queue());
    }

    /**
     * Resolves author and (if soft-deleted) deleter member references in
     * parallel, then assembles the embed. Returns a future so the caller can
     * chain a {@code reply} or {@code editMessage} when ready.
     */
    private CompletableFuture<MessageEmbed> buildEmbed(Guild guild, long channelId, HistoryEntry entry,
                                                       ShoutHistoryRepository.Position pos) {
        CompletableFuture<String> author = resolveMember(guild, entry.authorId());
        CompletableFuture<String> deleter = entry.deletion()
                .map(d -> resolveMember(guild, d.deletedBy()))
                .orElse(CompletableFuture.completedFuture(null));
        return author.thenCombine(deleter,
                (authorRef, deleterRef) -> ShoutHistoryView.embed(
                        entry, guild.getIdLong(), channelId, authorRef, deleterRef, pos));
    }

    private static long parseCursor(String customId) {
        int lastColon = customId.lastIndexOf(':');
        return Long.parseLong(customId.substring(lastColon + 1));
    }

    private static boolean canModerate(Member member, GuildChannel channel) {
        return Permissions.canManageMessages(member, channel);
    }

    /**
     * Returns a Discord user mention ({@code <@id>}) when the member still
     * exists in the guild, or the {@link #UNKNOWN_MEMBER} fallback otherwise.
     * Mentions in embed fields render as the user's name without producing a
     * notification, which is the desired UX here.
     */
    private static CompletableFuture<String> resolveMember(Guild guild, long userId) {
        return guild.retrieveMemberById(userId).submit()
                .thenApply(Member::getAsMention)
                .exceptionally(throwable -> {
                    Throwable cause = (throwable instanceof CompletionException ce) ? ce.getCause() : throwable;
                    if (cause instanceof ErrorResponseException ere
                            && ere.getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER) {
                        return UNKNOWN_MEMBER;
                    }
                    log.warn("Failed to resolve member {} in guild {}: {}",
                            userId, guild.getId(), cause.toString());
                    return UNKNOWN_MEMBER;
                });
    }
}
