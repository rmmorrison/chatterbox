package ca.ryanmorrison.chatterbox.features.shortener;

import java.security.SecureRandom;

/**
 * Generates short, case-insensitive alphanumeric tokens for the URL shortener.
 *
 * <p>Alphabet is base36 ({@code [a-z0-9]}); tokens are stored and matched in
 * lowercase so links remain shareable in case-mangling channels (chat clients,
 * email clients, etc.) without ambiguity.
 */
final class TokenGenerator {

    static final int LENGTH = 6;
    private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    private final SecureRandom random;

    TokenGenerator() {
        this(new SecureRandom());
    }

    TokenGenerator(SecureRandom random) {
        this.random = random;
    }

    String next() {
        char[] out = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            out[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(out);
    }
}
