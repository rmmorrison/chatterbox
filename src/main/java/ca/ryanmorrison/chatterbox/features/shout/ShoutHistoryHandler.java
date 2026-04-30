package ca.ryanmorrison.chatterbox.features.shout;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
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
 * latest history entry on slash invocation, then steps older / newer in
 * response to button clicks. Replies are ephemeral, so cross-user button
 * spoofing isn't a concern.
 */
final class ShoutHistoryHandler extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ShoutHistoryHandler.class);
    private static final String UNKNOWN_AUTHOR = "Former member";

    private final ShoutHistoryRepository history;

    ShoutHistoryHandler(ShoutHistoryRepository history) {
        this.history = history;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!ShoutHistoryView.CMD_NAME.equals(event.getName())) return;

        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("This command is only available in servers.").setEphemeral(true).queue();
            return;
        }

        long channelId = event.getChannel().getIdLong();
        Optional<HistoryEntry> latest = history.findLatest(channelId);
        if (latest.isEmpty()) {
            event.reply("No shout history for this channel yet.").setEphemeral(true).queue();
            return;
        }
        renderReply(event, guild, channelId, latest.get());
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith(ShoutHistoryView.BUTTON_PREFIX)) return;

        Guild guild = event.getGuild();
        if (guild == null) return;

        long channelId = event.getChannel().getIdLong();

        long currentId;
        Optional<HistoryEntry> next;
        try {
            if (id.startsWith(ShoutHistoryView.OLDER)) {
                currentId = Long.parseLong(id.substring(ShoutHistoryView.OLDER.length()));
                next = history.findOlder(channelId, currentId);
            } else if (id.startsWith(ShoutHistoryView.NEWER)) {
                currentId = Long.parseLong(id.substring(ShoutHistoryView.NEWER.length()));
                next = history.findNewer(channelId, currentId);
            } else {
                return;
            }
        } catch (NumberFormatException e) {
            log.warn("Malformed shout-history button id: {}", id);
            return;
        }

        if (next.isEmpty()) {
            // The neighbour vanished (e.g. underlying shout was just deleted). Fall back
            // to the current entry, or to whatever's still there in the channel.
            next = history.findById(channelId, currentId).or(() -> history.findLatest(channelId));
        }

        if (next.isEmpty()) {
            event.editMessage("No shout history for this channel anymore.")
                    .setEmbeds(List.of())
                    .setComponents(List.of())
                    .queue();
            return;
        }

        renderEdit(event, guild, channelId, next.get());
    }

    private void renderReply(SlashCommandInteractionEvent event, Guild guild, long channelId, HistoryEntry entry) {
        var older = history.findOlder(channelId, entry.historyId());
        var newer = history.findNewer(channelId, entry.historyId());
        var pos = history.position(channelId, entry.historyId());

        resolveDisplayName(guild, entry.authorId()).thenAccept(display -> {
            MessageEmbed embed = ShoutHistoryView.embed(entry, display, pos);
            event.replyEmbeds(embed)
                    .setEphemeral(true)
                    .setComponents(ShoutHistoryView.components(entry, older, newer))
                    .queue();
        });
    }

    private void renderEdit(ButtonInteractionEvent event, Guild guild, long channelId, HistoryEntry entry) {
        var older = history.findOlder(channelId, entry.historyId());
        var newer = history.findNewer(channelId, entry.historyId());
        var pos = history.position(channelId, entry.historyId());

        resolveDisplayName(guild, entry.authorId()).thenAccept(display -> {
            MessageEmbed embed = ShoutHistoryView.embed(entry, display, pos);
            event.editMessageEmbeds(embed)
                    .setComponents(ShoutHistoryView.components(entry, older, newer))
                    .queue();
        });
    }

    private static CompletableFuture<String> resolveDisplayName(Guild guild, long authorId) {
        return guild.retrieveMemberById(authorId).submit()
                .thenApply(Member::getEffectiveName)
                .exceptionally(throwable -> {
                    Throwable cause = (throwable instanceof CompletionException ce) ? ce.getCause() : throwable;
                    if (cause instanceof ErrorResponseException ere
                            && ere.getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER) {
                        return UNKNOWN_AUTHOR;
                    }
                    log.warn("Failed to resolve member {} in guild {}: {}",
                            authorId, guild.getId(), cause.toString());
                    return UNKNOWN_AUTHOR;
                });
    }
}
