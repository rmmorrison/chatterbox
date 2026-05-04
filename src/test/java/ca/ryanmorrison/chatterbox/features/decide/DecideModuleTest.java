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
}
