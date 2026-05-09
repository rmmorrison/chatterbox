package ca.ryanmorrison.chatterbox.features.shortener;

import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for the static {@code buildStatsEmbed} renderer behind
 * {@code /shorten stats}. The slash-command plumbing itself isn't worth
 * mocking (matches the pattern in {@link AutoShortenListenerTest}); the
 * embed builder is the bit with branches.
 */
class ShortenerHandlerTest {

    private static final String BASE_URL = "https://s.example";
    private static final long USER = 4242L;
    private static final long MOD = 9999L;
    private static final OffsetDateTime CREATED =
            OffsetDateTime.of(2026, 5, 1, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime CLICKED =
            OffsetDateTime.of(2026, 5, 9, 18, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime DELETED =
            OffsetDateTime.of(2026, 5, 9, 19, 0, 0, 0, ZoneOffset.UTC);

    private static String fieldValue(MessageEmbed embed, String name) {
        for (MessageEmbed.Field f : embed.getFields()) {
            if (name.equals(f.getName())) return f.getValue();
        }
        throw new AssertionError("Missing field: " + name + " in " + embed.getFields());
    }

    private static List<String> fieldNames(MessageEmbed embed) {
        return embed.getFields().stream().map(MessageEmbed.Field::getName).toList();
    }

    @Test
    void liveZeroClicksRendersNeverLastClicked() {
        ShortenedUrl entry = new ShortenedUrl(
                1L, "abc123", "https://example.com/x",
                USER, CREATED,
                Optional.empty(), Optional.empty(),
                0L, Optional.empty());

        MessageEmbed embed = ShortenerHandler.buildStatsEmbed(entry, BASE_URL);

        assertEquals("Click stats: abc123", embed.getTitle());
        assertEquals("`https://s.example/abc123`", fieldValue(embed, "Short URL"));
        assertEquals("https://example.com/x", fieldValue(embed, "Destination"));
        assertEquals("0", fieldValue(embed, "Clicks"));
        assertEquals("*Never*", fieldValue(embed, "Last clicked"));
        assertTrue(fieldValue(embed, "Created").contains("<@" + USER + ">"));
        assertTrue(fieldValue(embed, "Created").contains("<t:" + CREATED.toEpochSecond() + ":R>"));
        // Live entries should not surface a Deleted row.
        assertTrue(fieldNames(embed).stream().noneMatch("Deleted"::equals));
    }

    @Test
    void liveWithClicksRendersCountAndTimestamp() {
        ShortenedUrl entry = new ShortenedUrl(
                1L, "abc123", "https://example.com/x",
                USER, CREATED,
                Optional.empty(), Optional.empty(),
                42L, Optional.of(CLICKED));

        MessageEmbed embed = ShortenerHandler.buildStatsEmbed(entry, BASE_URL);

        assertEquals("42", fieldValue(embed, "Clicks"));
        assertEquals("<t:" + CLICKED.toEpochSecond() + ":R>",
                fieldValue(embed, "Last clicked"));
    }

    @Test
    void deletedEntryIncludesDeletionLineAndGreyColor() {
        ShortenedUrl entry = new ShortenedUrl(
                1L, "abc123", "https://example.com/x",
                USER, CREATED,
                Optional.of(DELETED), Optional.of(MOD),
                7L, Optional.of(CLICKED));

        MessageEmbed embed = ShortenerHandler.buildStatsEmbed(entry, BASE_URL);

        assertEquals(0x90A4AE, embed.getColorRaw());
        String deleted = fieldValue(embed, "Deleted");
        assertTrue(deleted.contains("<t:" + DELETED.toEpochSecond() + ":R>"),
                "expected delete timestamp, got: " + deleted);
        assertTrue(deleted.contains("<@" + MOD + ">"),
                "expected deleter mention, got: " + deleted);
        // Click count is preserved even after deletion — admins still want the
        // historical figure.
        assertEquals("7", fieldValue(embed, "Clicks"));
    }

    @Test
    void liveEntryUsesBlueAccentColor() {
        ShortenedUrl entry = new ShortenedUrl(
                1L, "abc123", "https://example.com/x",
                USER, CREATED,
                Optional.empty(), Optional.empty(),
                0L, Optional.empty());

        MessageEmbed embed = ShortenerHandler.buildStatsEmbed(entry, BASE_URL);
        assertEquals(0x4F8DDC, embed.getColorRaw());
    }

    @Test
    void baseUrlWithTrailingSlashStillRendersOneSlash() {
        ShortenedUrl entry = new ShortenedUrl(
                1L, "abc123", "https://example.com/x",
                USER, CREATED,
                Optional.empty(), Optional.empty(),
                0L, Optional.empty());

        MessageEmbed embed = ShortenerHandler.buildStatsEmbed(entry, "https://s.example/");
        assertEquals("`https://s.example/abc123`", fieldValue(embed, "Short URL"));
    }

    @Test
    void deletedWithoutDeleterStillRenders() {
        // Defensive: deleted_at present but deleted_by null shouldn't NPE.
        ShortenedUrl entry = new ShortenedUrl(
                1L, "abc123", "https://example.com/x",
                USER, CREATED,
                Optional.of(DELETED), Optional.empty(),
                0L, Optional.empty());

        MessageEmbed embed = ShortenerHandler.buildStatsEmbed(entry, BASE_URL);
        assertNotNull(fieldValue(embed, "Deleted"));
    }
}
