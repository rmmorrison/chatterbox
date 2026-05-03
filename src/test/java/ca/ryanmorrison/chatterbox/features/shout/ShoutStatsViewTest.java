package ca.ryanmorrison.chatterbox.features.shout;

import ca.ryanmorrison.chatterbox.features.shout.ShoutStats.ReplayedShout;
import ca.ryanmorrison.chatterbox.features.shout.ShoutStats.ShoutSummary;
import ca.ryanmorrison.chatterbox.features.shout.ShoutStats.ShouterCount;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShoutStatsViewTest {

    private static final long GUILD = 100L;
    private static final long CHANNEL = 200L;
    private static final OffsetDateTime T =
            OffsetDateTime.of(2026, 5, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void emptyEmbedReadsClearly() {
        MessageEmbed embed = ShoutStatsView.embedEmpty();
        assertEquals("Shout stats", embed.getTitle());
        assertNotNull(embed.getDescription());
        assertTrue(embed.getDescription().contains("No shouts"));
    }

    @Test
    void leaderboardUsesMedalsAndPluralisesShouts() {
        var rows = List.of(
                new ShouterCount(1L, 7),
                new ShouterCount(2L, 1),
                new ShouterCount(3L, 4));
        var refs = List.of("<@1>", "<@2>", "<@3>");
        String rendered = ShoutStatsView.renderLeaderboard(rows, refs);

        assertTrue(rendered.contains("🥇 <@1> — 7 shouts"));
        assertTrue(rendered.contains("🥈 <@2> — 1 shout"),
                "single shout should be singular");
        assertTrue(rendered.contains("🥉 <@3> — 4 shouts"));
    }

    @Test
    void leaderboardRefsLengthMustMatch() {
        var rows = List.of(new ShouterCount(1L, 7), new ShouterCount(2L, 4));
        var refs = List.of("<@1>");
        assertThrows(IllegalArgumentException.class,
                () -> ShoutStatsView.renderLeaderboard(rows, refs));
    }

    @Test
    void embedIncludesTotalsAndAllSections() {
        var oldest = new ShoutSummary(1L, 1001L, 11L, "FIRST EVER", T);
        var newest = new ShoutSummary(2L, 1002L, 22L, "LATEST", T.plusDays(1));
        var longest = new ShoutSummary(3L, 1003L, 33L, "X".repeat(123), T.plusHours(5));
        var hallShout = new ShoutSummary(4L, 1004L, 44L, "MEMETIC GOLD", T.plusHours(8));
        var snapshot = new ShoutStats(
                42, 6, 9,
                List.of(new ShouterCount(11L, 12), new ShouterCount(22L, 7)),
                Optional.of(oldest), Optional.of(newest), Optional.of(longest),
                Optional.of(new ReplayedShout(hallShout, 5)));

        MessageEmbed embed = ShoutStatsView.embed(
                snapshot, GUILD, CHANNEL,
                List.of("<@11>", "<@22>"),
                "<@11>", "<@22>", "<@33>", "<@44>");

        Map<String, MessageEmbed.Field> fields = fieldsByName(embed);
        assertEquals("42", fields.get("Total shouts").getValue());
        assertEquals("6",  fields.get("Distinct shouters").getValue());
        assertEquals("9",  fields.get("Last 7 days").getValue());

        assertTrue(fields.containsKey("Top shouters"));
        assertTrue(fields.containsKey("First shout"));
        assertTrue(fields.containsKey("Most recent"));
        assertTrue(fields.keySet().stream().anyMatch(k -> k.startsWith("Longest (123 chars)")));
        assertTrue(fields.keySet().stream().anyMatch(k -> k.startsWith("Hall of fame (replayed 5 times)")));
    }

    @Test
    void hallOfFameSingularWhenReplayedOnce() {
        var hallShout = new ShoutSummary(4L, 1004L, 44L, "ONLY ONCE", T);
        var snapshot = new ShoutStats(
                1, 1, 1,
                List.of(new ShouterCount(11L, 1)),
                Optional.of(hallShout), Optional.of(hallShout), Optional.of(hallShout),
                Optional.of(new ReplayedShout(hallShout, 1)));
        MessageEmbed embed = ShoutStatsView.embed(snapshot, GUILD, CHANNEL,
                List.of("<@11>"), "<@11>", "<@11>", "<@11>", "<@11>");
        assertTrue(fieldsByName(embed).keySet().stream()
                .anyMatch(k -> k.startsWith("Hall of fame (replayed 1 time)")),
                "single replay should read as 1 time");
    }

    @Test
    void shoutLineIncludesAuthorTimestampAndJumpLink() {
        var s = new ShoutSummary(1L, 9999L, 11L, "HELLO", T);
        String line = ShoutStatsView.renderShoutLine(s, GUILD, CHANNEL, "<@11>");
        assertTrue(line.contains("<@11>"));
        assertTrue(line.contains("<t:" + T.toEpochSecond() + ":F>"));
        assertTrue(line.contains("https://discord.com/channels/" + GUILD + "/" + CHANNEL + "/9999"));
    }

    @Test
    void previewTruncatesLongContentAndQuotesIt() {
        String huge = "X".repeat(ShoutStatsView.CONTENT_PREVIEW_LIMIT + 50);
        var s = new ShoutSummary(1L, 9999L, 11L, huge, T);
        String preview = ShoutStatsView.renderShoutPreview(s, GUILD, CHANNEL, "<@11>");
        assertTrue(preview.contains("\n> "));
        assertTrue(preview.contains("…"));
        assertFalse(preview.contains(huge), "raw long content should not pass through unredacted");
    }

    @Test
    void truncateLeavesShortStringsAlone() {
        assertEquals("HI", ShoutStatsView.truncate("HI", 100));
        assertEquals("ABCD…", ShoutStatsView.truncate("ABCDEFGH", 5));
    }

    @Test
    void embedSkipsOptionalSectionsWhenAbsent() {
        var snapshot = new ShoutStats(
                3, 1, 3,
                List.of(new ShouterCount(11L, 3)),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        MessageEmbed embed = ShoutStatsView.embed(snapshot, GUILD, CHANNEL,
                List.of("<@11>"), null, null, null, null);
        Map<String, MessageEmbed.Field> fields = fieldsByName(embed);
        assertFalse(fields.containsKey("First shout"));
        assertFalse(fields.containsKey("Most recent"));
        assertFalse(fields.keySet().stream().anyMatch(k -> k.startsWith("Longest")));
        assertFalse(fields.keySet().stream().anyMatch(k -> k.startsWith("Hall of fame")));
    }

    private static Map<String, MessageEmbed.Field> fieldsByName(MessageEmbed embed) {
        return embed.getFields().stream()
                .collect(java.util.stream.Collectors.toMap(MessageEmbed.Field::getName, f -> f));
    }
}
