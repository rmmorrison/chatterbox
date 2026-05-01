package ca.ryanmorrison.chatterbox.features.shout;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShoutHistoryViewTest {

    private static final OffsetDateTime AUTHORED_AT =
            OffsetDateTime.of(2026, 4, 30, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime DELETED_AT =
            OffsetDateTime.of(2026, 4, 30, 13, 0, 0, 0, ZoneOffset.UTC);
    private static final long EPOCH = AUTHORED_AT.toEpochSecond();
    private static final long GUILD = 1111L;
    private static final long CHANNEL = 2222L;
    private static final long MESSAGE = 3333L;
    private static final long AUTHOR = 7777L;
    private static final long DELETER = 9999L;

    private static HistoryEntry active(long id) {
        return new HistoryEntry(id, 700L + id, MESSAGE + id, "WHY ARE WE STILL HERE", AUTHOR, AUTHORED_AT,
                Optional.empty());
    }

    private static HistoryEntry deleted(long id) {
        return new HistoryEntry(id, 700L + id, MESSAGE + id, "WHY ARE WE STILL HERE", AUTHOR, AUTHORED_AT,
                Optional.of(new HistoryEntry.Deletion(DELETER, DELETED_AT)));
    }

    @Test
    void embedIncludesContentAuthorTimestampSourceAndPosition() {
        HistoryEntry entry = active(42L);
        MessageEmbed embed = ShoutHistoryView.embed(
                entry, GUILD, CHANNEL, "<@" + AUTHOR + ">", null,
                new ShoutHistoryRepository.Position(2, 5));

        assertEquals("Shout history", embed.getTitle());
        assertEquals("WHY ARE WE STILL HERE", embed.getDescription());
        assertEquals("Entry 2 of 5", embed.getFooter().getText());
        assertNull(embed.getColor(), "active entries don't have a color override");

        String expectedJump = "https://discord.com/channels/" + GUILD + "/" + CHANNEL + "/" + entry.messageId();

        var fields = embed.getFields();
        assertEquals(3, fields.size());
        assertEquals("Author", fields.get(0).getName());
        assertEquals("<@" + AUTHOR + ">", fields.get(0).getValue());
        assertEquals("Originally written", fields.get(1).getName());
        assertEquals("<t:" + EPOCH + ":F>", fields.get(1).getValue());
        assertEquals("Source", fields.get(2).getName());
        assertEquals("[Jump to message](" + expectedJump + ")", fields.get(2).getValue());
    }

    @Test
    void embedAuthorFallsBackToPlainTextWhenMemberIsGone() {
        MessageEmbed embed = ShoutHistoryView.embed(
                active(42L), GUILD, CHANNEL, "Former member", null,
                new ShoutHistoryRepository.Position(1, 1));

        assertEquals("Former member", embed.getFields().get(0).getValue());
    }

    @Test
    void deletedEntryGetsRedColorAndDeletedByField() {
        MessageEmbed embed = ShoutHistoryView.embed(
                deleted(42L), GUILD, CHANNEL, "<@" + AUTHOR + ">", "<@" + DELETER + ">",
                new ShoutHistoryRepository.Position(2, 5));

        assertEquals(new Color(0xED, 0x42, 0x45).getRGB(), embed.getColor().getRGB());

        var fields = embed.getFields();
        assertEquals(4, fields.size());
        assertEquals("Deleted by", fields.get(3).getName());
        assertEquals("<@" + DELETER + ">", fields.get(3).getValue());
    }

    @Test
    void noButtonsWhenStandingAloneAndNotModerator() {
        List<ActionRow> components = ShoutHistoryView.components(
                active(42L), Optional.empty(), Optional.empty(), false);
        assertTrue(components.isEmpty());
    }

    @Test
    void onlyOlderButtonAtNewestEnd() {
        List<ActionRow> components = ShoutHistoryView.components(
                active(42L), Optional.of(active(41L)), Optional.empty(), false);
        var buttons = components.get(0).getButtons();
        assertEquals(1, buttons.size());
        assertButton(buttons.get(0), "← Older", ShoutHistoryView.OLDER + 42L, ButtonStyle.SECONDARY);
    }

    @Test
    void onlyNewerButtonAtOldestEnd() {
        List<ActionRow> components = ShoutHistoryView.components(
                active(42L), Optional.empty(), Optional.of(active(43L)), false);
        var buttons = components.get(0).getButtons();
        assertEquals(1, buttons.size());
        assertButton(buttons.get(0), "Newer →", ShoutHistoryView.NEWER + 42L, ButtonStyle.SECONDARY);
    }

    @Test
    void bothButtonsInTheMiddle() {
        List<ActionRow> components = ShoutHistoryView.components(
                active(42L), Optional.of(active(41L)), Optional.of(active(43L)), false);
        var buttons = components.get(0).getButtons();
        assertEquals(2, buttons.size());
        assertButton(buttons.get(0), "← Older", ShoutHistoryView.OLDER + 42L, ButtonStyle.SECONDARY);
        assertButton(buttons.get(1), "Newer →", ShoutHistoryView.NEWER + 42L, ButtonStyle.SECONDARY);
    }

    @Test
    void moderatorOnActiveEntryGetsDeleteButton() {
        List<ActionRow> components = ShoutHistoryView.components(
                active(42L), Optional.of(active(41L)), Optional.of(active(43L)), true);
        var buttons = components.get(0).getButtons();
        assertEquals(3, buttons.size());
        assertButton(buttons.get(0), "← Older",  ShoutHistoryView.OLDER + 42L,  ButtonStyle.SECONDARY);
        assertButton(buttons.get(1), "Newer →",  ShoutHistoryView.NEWER + 42L,  ButtonStyle.SECONDARY);
        assertButton(buttons.get(2), "Delete",   ShoutHistoryView.DELETE + 42L, ButtonStyle.DANGER);
    }

    @Test
    void moderatorOnDeletedEntryGetsRestoreButton() {
        List<ActionRow> components = ShoutHistoryView.components(
                deleted(42L), Optional.of(active(41L)), Optional.empty(), true);
        var buttons = components.get(0).getButtons();
        assertEquals(2, buttons.size());
        assertButton(buttons.get(0), "← Older", ShoutHistoryView.OLDER   + 42L, ButtonStyle.SECONDARY);
        assertButton(buttons.get(1), "Restore", ShoutHistoryView.RESTORE + 42L, ButtonStyle.SUCCESS);
    }

    @Test
    void moderatorOnSoloActiveEntryStillGetsDeleteButton() {
        List<ActionRow> components = ShoutHistoryView.components(
                active(42L), Optional.empty(), Optional.empty(), true);
        var buttons = components.get(0).getButtons();
        assertEquals(1, buttons.size());
        assertButton(buttons.get(0), "Delete", ShoutHistoryView.DELETE + 42L, ButtonStyle.DANGER);
    }

    private static void assertButton(Button button, String expectedLabel, String expectedId, ButtonStyle expectedStyle) {
        assertEquals(expectedLabel, button.getLabel());
        assertEquals(expectedId, button.getCustomId());
        assertEquals(expectedStyle, button.getStyle());
    }
}
