package ca.ryanmorrison.chatterbox.features.shout;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShoutDetectorTest {

    private final ShoutDetector detector = new ShoutDetector();

    @ParameterizedTest
    @ValueSource(strings = {
            "WHY ARE WE STILL HERE",
            "I AM YELLING ABOUT THIS",
            "BUY $100 NOW BEFORE IT'S GONE",
            "I AM YELLING <@123456789> CHECK THIS OUT",
            "WHAT IS HTTPS://EXAMPLE.COM EVEN DOING",
            "GOOD MORNING <:wave:123> EVERYONE",
            "THIS    HAS    EXTRA    SPACES",
            "STOP!!!! THIS IS REALLY ANNOYING"
    })
    void positives(String input) {
        assertTrue(detector.isShouting(input), "expected to be classified as shouting: " + input);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "$100",
            "https://EXAMPLE.COM",
            "WWW.EXAMPLE.COM",
            "LOL",                          // below min letters
            "OMG",                          // below min letters
            "Hello world",                  // mixed case
            "I am ANGRY about this",        // not strict all-caps
            "i am angry about this",        // all lowercase
            "<@123456789> <:wave:1> <#42>", // only Discord tokens
            "12345 67890 !!!",              // no letters
            "",                             // empty
            "    ",                         // whitespace
            "你好世界你好世界"                  // CJK only — no cased letters
    })
    void negatives(String input) {
        assertFalse(detector.isShouting(input), "expected NOT to be classified as shouting: " + input);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            HELL,                   false
            HEL!,                   false
            HELLO,                  true
            HELL!,                  false
            HELLOO,                 true
            HELLO WORLD,            true
            """)
    void minLetterBoundary(String input, boolean expected) {
        assertTrue(detector.isShouting(input) == expected,
                "input='" + input + "' expected=" + expected + " got=" + detector.isShouting(input));
    }

    @org.junit.jupiter.api.Test
    void nullIsNotShouting() {
        assertFalse(detector.isShouting(null));
    }
}
