package ca.ryanmorrison.chatterbox.features.shout;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShoutHistoryViewTest {

    private static final OffsetDateTime AUTHORED_AT =
            OffsetDateTime.of(2026, 4, 30, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final long EPOCH = AUTHORED_AT.toEpochSecond();

    private static HistoryEntry entry(long id) {
        return new HistoryEntry(id, "WHY ARE WE STILL HERE", 7777L, AUTHORED_AT);
    }

    @Test
    void embedIncludesContentAuthorTimestampAndPosition() {
        MessageEmbed embed = ShoutHistoryView.embed(
                entry(42L), "Alice", new ShoutHistoryRepository.Position(2, 5));

        assertEquals("Shout history", embed.getTitle());
        assertEquals("WHY ARE WE STILL HERE", embed.getDescription());
        assertEquals("Entry 2 of 5", embed.getFooter().getText());

        var fields = embed.getFields();
        assertEquals("Author", fields.get(0).getName());
        assertEquals("Alice", fields.get(0).getValue());
        assertEquals("Originally written", fields.get(1).getName());
        assertEquals("<t:" + EPOCH + ":F>", fields.get(1).getValue());
    }

    @Test
    void noButtonsWhenStandingAlone() {
        List<ActionRow> components = ShoutHistoryView.components(
                entry(42L), Optional.empty(), Optional.empty());
        assertTrue(components.isEmpty());
    }

    @Test
    void onlyOlderButtonAtNewestEnd() {
        List<ActionRow> components = ShoutHistoryView.components(
                entry(42L), Optional.of(entry(41L)), Optional.empty());
        assertEquals(1, components.size());
        var buttons = components.get(0).getButtons();
        assertEquals(1, buttons.size());
        assertButton(buttons.get(0), "← Older", ShoutHistoryView.OLDER + 42L);
    }

    @Test
    void onlyNewerButtonAtOldestEnd() {
        List<ActionRow> components = ShoutHistoryView.components(
                entry(42L), Optional.empty(), Optional.of(entry(43L)));
        assertEquals(1, components.size());
        var buttons = components.get(0).getButtons();
        assertEquals(1, buttons.size());
        assertButton(buttons.get(0), "Newer →", ShoutHistoryView.NEWER + 42L);
    }

    @Test
    void bothButtonsInTheMiddle() {
        List<ActionRow> components = ShoutHistoryView.components(
                entry(42L), Optional.of(entry(41L)), Optional.of(entry(43L)));
        assertEquals(1, components.size());
        var buttons = components.get(0).getButtons();
        assertEquals(2, buttons.size());
        assertButton(buttons.get(0), "← Older", ShoutHistoryView.OLDER + 42L);
        assertButton(buttons.get(1), "Newer →", ShoutHistoryView.NEWER + 42L);
    }

    private static void assertButton(Button button, String expectedLabel, String expectedId) {
        assertEquals(expectedLabel, button.getLabel());
        assertEquals(expectedId, button.getCustomId());
    }
}
