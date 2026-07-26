package ca.ryanmorrison.chatterbox.features.decide;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The slash-handling glue is exercised on staging; this covers the pure renderer. */
class DecideModuleTest {

    @Test
    void singleOptionOmitsFromLine() {
        String rendered = DecideModule.renderResult("yes", List.of("yes"));
        assertEquals("🎲 **yes**", rendered);
    }

    @Test
    void multipleOptionsIncludeFromLine() {
        String rendered = DecideModule.renderResult("tacos", List.of("pizza", "tacos", "sushi"));
        assertTrue(rendered.startsWith("🎲 **tacos**"));
        assertTrue(rendered.contains("\n_(from: pizza · tacos · sushi)_"));
    }

    @Test
    void hugeFromLineIsTruncated() {
        // 200 options of ~10 chars each → ~2000 char joined line, well over the cap.
        List<String> options = java.util.stream.IntStream.range(0, 200)
                .mapToObj(i -> "option" + i)
                .toList();
        String rendered = DecideModule.renderResult("option0", options);
        assertTrue(rendered.contains("…"), "should ellipsise an overlong from-line");
        assertFalse(rendered.length() > 2000,
                "rendered message must remain under Discord's 2000 char cap");
    }

    @Test
    void oversizedSingleOptionIsTruncatedToDiscordsLimit() {
        // One whitespace-free option means options.size() == 1, which skips the
        // truncated "from" line -- nothing else used to bound the output.
        String huge = "x".repeat(6000);
        String out = DecideModule.renderResult(huge, java.util.List.of(huge));
        assertTrue(out.length() <= DecideModule.MAX_MESSAGE_LENGTH,
                () -> "rendered " + out.length() + " chars");
    }

    @Test
    void oversizedManyOptionsAreTruncatedToDiscordsLimit() {
        java.util.List<String> many = new java.util.ArrayList<>();
        for (int i = 0; i < 400; i++) many.add("option-" + i + "-" + "y".repeat(20));
        String out = DecideModule.renderResult(many.get(0), many);
        assertTrue(out.length() <= DecideModule.MAX_MESSAGE_LENGTH,
                () -> "rendered " + out.length() + " chars");
    }

    @Test
    void truncationNeverSplitsASurrogatePair() {
        String emoji = "\uD83C\uDF89".repeat(2000); // party popper
        String out = DecideModule.renderResult(emoji, java.util.List.of(emoji));
        assertTrue(out.length() <= DecideModule.MAX_MESSAGE_LENGTH);
        assertFalse(Character.isHighSurrogate(out.charAt(out.length() - 2)),
                "a lone high surrogate would render as a replacement char");
    }
}
