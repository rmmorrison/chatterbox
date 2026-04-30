package ca.ryanmorrison.chatterbox.features.shout;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pure rendering for the {@code /shout-history} response — given an entry,
 * neighbour-availability flags, the resolved author display name, and the
 * position-in-channel info, produces the embed and the action row of buttons.
 *
 * <p>No JDA REST calls or DB access happen here, which keeps the visible
 * output unit-testable.
 */
final class ShoutHistoryView {

    static final String CMD_NAME = "shout-history";
    static final String BUTTON_PREFIX = CMD_NAME + ":";
    static final String OLDER = BUTTON_PREFIX + "older:";
    static final String NEWER = BUTTON_PREFIX + "newer:";

    private ShoutHistoryView() {}

    static MessageEmbed embed(HistoryEntry entry, String authorDisplayName, ShoutHistoryRepository.Position pos) {
        long ts = entry.authoredAt().toEpochSecond();
        return new EmbedBuilder()
                .setTitle("Shout history")
                .setDescription(entry.content())
                .addField("Author", authorDisplayName, true)
                .addField("Originally written", "<t:" + ts + ":F>", true)
                .setFooter("Entry " + pos.rank() + " of " + pos.total())
                .build();
    }

    /**
     * Returns an empty list when the entry stands alone (no buttons make
     * sense), otherwise an action row with whichever of Older / Newer apply.
     */
    static List<ActionRow> components(HistoryEntry entry,
                                      Optional<HistoryEntry> older,
                                      Optional<HistoryEntry> newer) {
        var buttons = new ArrayList<Button>();
        older.ifPresent(o -> buttons.add(Button.secondary(OLDER + entry.historyId(), "← Older")));
        newer.ifPresent(n -> buttons.add(Button.secondary(NEWER + entry.historyId(), "Newer →")));
        return buttons.isEmpty() ? List.of() : List.of(ActionRow.of(buttons));
    }
}
