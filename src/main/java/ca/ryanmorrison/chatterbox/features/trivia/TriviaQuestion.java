package ca.ryanmorrison.chatterbox.features.trivia;

import java.util.List;

/**
 * Decoded, ready-to-render trivia question. Type is normalised to
 * {@link Type#MULTIPLE} (4 choices) or {@link Type#BOOLEAN} (true/false).
 */
record TriviaQuestion(
        Type type,
        String difficulty,
        String category,
        String question,
        String correctAnswer,
        List<String> incorrectAnswers) {

    enum Type { MULTIPLE, BOOLEAN }
}
