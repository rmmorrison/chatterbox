package ca.ryanmorrison.chatterbox.features.shout;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pure rendering for the {@code /shout history} response. Given an entry, the
 * resolved author display name, the deleter display name (only relevant when
 * the entry is deleted and the viewer is a moderator), the position info, and
 * a {@code viewerCanModerate} flag, produces the embed and button row.
 *
 * <p>No JDA REST calls or DB access happen here, which keeps the visible
 * output unit-testable.
 */
final class ShoutHistoryView {

    static final String SUBCOMMAND = "history";
    /**
     * Stable namespace for the button component IDs; deliberately distinct
     * from the slash command path so future {@code /shout} subcommands can't
     * accidentally collide with active button interactions.
     */
    static final String BUTTON_PREFIX = "shout-history:";
    static final String OLDER   = BUTTON_PREFIX + "older:";
    static final String NEWER   = BUTTON_PREFIX + "newer:";
    static final String DELETE  = BUTTON_PREFIX + "delete:";
    static final String RESTORE = BUTTON_PREFIX + "restore:";

    private static final Color DELETED_COLOR = new Color(0xED, 0x42, 0x45); // Discord-ish red

    private ShoutHistoryView() {}

    /**
     * Builds the embed.
     *
     * <p>{@code authorReference} and {@code deleterReference} are rendered
     * verbatim into the embed: typically a {@code <@id>} mention when the
     * member still exists, or a plain-text fallback string otherwise.
     *
     * @param deleterReference  ignored unless {@code entry.deletion()} is
     *                          present; required when present.
     */
    static MessageEmbed embed(HistoryEntry entry,
                              long guildId,
                              long channelId,
                              String authorReference,
                              String deleterReference,
                              ShoutHistoryRepository.Position pos) {
        long ts = entry.authoredAt().toEpochSecond();
        String jumpUrl = "https://discord.com/channels/" + guildId + "/" + channelId + "/" + entry.messageId();
        var builder = new EmbedBuilder()
                .setTitle("Shout history")
                .setDescription(entry.content())
                .addField("Author", authorReference, true)
                .addField("Originally written", "<t:" + ts + ":F>", true)
                .addField("Source", "[Jump to message](" + jumpUrl + ")", true)
                .setFooter("Entry " + pos.rank() + " of " + pos.total());

        if (entry.deletion().isPresent()) {
            builder.setColor(DELETED_COLOR);
            if (deleterReference != null) {
                builder.addField("Deleted by", deleterReference, true);
            }
        }
        return builder.build();
    }

    /**
     * Returns an empty list when no buttons make sense, otherwise an action
     * row with whichever of Older / Newer / Delete-or-Restore apply.
     */
    static List<ActionRow> components(HistoryEntry entry,
                                      Optional<HistoryEntry> older,
                                      Optional<HistoryEntry> newer,
                                      boolean viewerCanModerate) {
        var buttons = new ArrayList<Button>();
        older.ifPresent(o -> buttons.add(Button.secondary(OLDER + entry.historyId(), "← Older")));
        newer.ifPresent(n -> buttons.add(Button.secondary(NEWER + entry.historyId(), "Newer →")));
        if (viewerCanModerate) {
            if (entry.deletion().isPresent()) {
                buttons.add(Button.success(RESTORE + entry.historyId(), "Restore"));
            } else {
                buttons.add(Button.danger(DELETE + entry.historyId(), "Delete"));
            }
        }
        return buttons.isEmpty() ? List.of() : List.of(ActionRow.of(buttons));
    }
}
