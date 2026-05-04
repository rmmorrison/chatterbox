package ca.ryanmorrison.chatterbox.features.emote;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmoteTest {

    @Test
    void everyEmoteHasNonBlankFields() {
        for (Emote e : Emote.values()) {
            assertTrue(e.value() != null && !e.value().isBlank(),
                    "emote " + e.name() + " missing value");
            assertTrue(e.label() != null && !e.label().isBlank(),
                    "emote " + e.name() + " missing label");
            assertTrue(e.text() != null && !e.text().isBlank(),
                    "emote " + e.name() + " missing text");
        }
    }

    @Test
    void valuesAreUnique() {
        Set<String> values = java.util.Arrays.stream(Emote.values())
                .map(Emote::value)
                .collect(Collectors.toSet());
        assertEquals(Emote.values().length, values.size(),
                "duplicate value among emotes");
    }

    @Test
    void textsAreUnique() {
        Set<String> texts = new HashSet<>();
        for (Emote e : Emote.values()) texts.add(e.text());
        assertEquals(Emote.values().length, texts.size(),
                "two emotes share the same text");
    }

    @Test
    void fromValueLooksUpKnown() {
        assertEquals(Emote.FLIP,  Emote.fromValue("flip"));
        assertEquals(Emote.SHRUG, Emote.fromValue("shrug"));
        assertEquals(Emote.LENNY, Emote.fromValue("lenny"));
    }

    @Test
    void fromValueRejectsUnknown() {
        assertThrows(IllegalArgumentException.class,
                () -> Emote.fromValue("bogus"));
    }

    @Test
    void everyEmoteFitsInADiscordMessage() {
        // 2000-char cap; sanity check none of the canned strings get carried away.
        for (Emote e : Emote.values()) {
            assertTrue(e.text().length() < 2000,
                    "emote " + e.name() + " too long for a Discord message");
        }
    }
}
