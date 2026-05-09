package ca.ryanmorrison.chatterbox.features.trivia;

import java.util.List;
import java.util.Set;

/**
 * Immutable snapshot of one in-flight trivia round.
 *
 * <p>Field semantics:
 * <ul>
 *   <li>{@code roundId} — short opaque id baked into each button's
 *       {@code custom_id}; lookups in {@link TriviaRounds} key on this.</li>
 *   <li>{@code shuffledChoices} — the labels in the order shown to users.
 *       {@code correctIndex} indexes into this list.</li>
 *   <li>{@code messageId} / {@code channelId} — the public message that
 *       carries the question + buttons; needed for edit-on-resolve.</li>
 *   <li>{@code wrongAnswerers} — users who already burned their one shot
 *       with a wrong guess. Live users (no entry) can still try.</li>
 * </ul>
 */
record TriviaRound(
        String roundId,
        long messageId,
        long channelId,
        TriviaQuestion question,
        List<String> shuffledChoices,
        int correctIndex,
        Set<Long> wrongAnswerers) {

    TriviaRound withWrongAnswerer(long userId) {
        java.util.LinkedHashSet<Long> next = new java.util.LinkedHashSet<>(wrongAnswerers);
        next.add(userId);
        return new TriviaRound(roundId, messageId, channelId, question,
                shuffledChoices, correctIndex, java.util.Collections.unmodifiableSet(next));
    }

    String correctAnswerLabel() {
        return shuffledChoices.get(correctIndex);
    }
}
