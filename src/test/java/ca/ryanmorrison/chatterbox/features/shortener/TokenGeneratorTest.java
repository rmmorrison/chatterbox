package ca.ryanmorrison.chatterbox.features.shortener;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenGeneratorTest {

    private static final Pattern ALPHABET = Pattern.compile("^[a-z0-9]+$");

    @Test
    void tokensAreSixLowercaseAlphanumeric() {
        TokenGenerator gen = new TokenGenerator();
        for (int i = 0; i < 100; i++) {
            String t = gen.next();
            assertEquals(6, t.length(), "expected 6 chars, got: " + t);
            assertTrue(ALPHABET.matcher(t).matches(), "alphabet violation: " + t);
        }
    }

    @Test
    void tokensVaryAcrossInvocations() {
        TokenGenerator gen = new TokenGenerator(new SecureRandom());
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) seen.add(gen.next());
        // 200 samples from a 36^6 ≈ 2.18B space — collisions are vanishingly improbable.
        assertTrue(seen.size() > 195, "unexpected duplication: only " + seen.size() + " unique tokens");
    }
}
