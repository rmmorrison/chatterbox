package ca.ryanmorrison.chatterbox.features.trivia;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable snapshot of one in-flight trivia round inside a
 * {@link TriviaGame}.
 *
 * <p>Field semantics:
 * <ul>
 *   <li>{@code roundId} — short opaque id baked into each button's
 *       {@code custom_id}; lookups in {@link TriviaRounds} key on this.</li>
 *   <li>{@code gameId} — back-reference so a button click can find the
 *       owning game without scanning.</li>
 *   <li>{@code roundNumber} / {@code totalRounds} — 1-based progress
 *       indicator shown in the embed.</li>
 *   <li>{@code shuffledChoices} — labels in display order;
 *       {@code correctIndex} indexes into this list.</li>
 *   <li>{@code messageId} / {@code channelId} — the public message that
 *       carries the question + buttons; needed for edit-on-resolve.</li>
 *   <li>{@code wrongAnswerers} — users who already burned their one shot
 *       with a wrong guess.</li>
 * </ul>
 */
record TriviaRound(
        String roundId,
        String gameId,
        int roundNumber,
        int totalRounds,
        long messageId,
        long channelId,
        TriviaQuestion question,
        List<String> shuffledChoices,
        int correctIndex,
        Set<Long> wrongAnswerers) {

    TriviaRound withWrongAnswerer(long userId) {
        LinkedHashSet<Long> next = new LinkedHashSet<>(wrongAnswerers);
        next.add(userId);
        return new TriviaRound(roundId, gameId, roundNumber, totalRounds,
                messageId, channelId, question,
                shuffledChoices, correctIndex, Collections.unmodifiableSet(next));
    }

    TriviaRound withMessage(long messageId, long channelId) {
        return new TriviaRound(roundId, gameId, roundNumber, totalRounds,
                messageId, channelId, question,
                shuffledChoices, correctIndex, wrongAnswerers);
    }

    String correctAnswerLabel() {
        return shuffledChoices.get(correctIndex);
    }
}
