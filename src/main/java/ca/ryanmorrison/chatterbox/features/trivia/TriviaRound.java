package ca.ryanmorrison.chatterbox.features.trivia;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable snapshot of one in-flight trivia round inside a
 * {@link TriviaGame}.
 *
 * <p>All joined players get one shot per round. The round resolves when
 * either every joined player has answered (early end) or its timer fires.
 * {@code answers} maps userId → chosen choice index; the per-player
 * outcome (correct / wrong-with-pick / no-answer) is computed at
 * resolution time from this map plus {@link #joinedPlayers} and
 * {@link #correctIndex}.
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
        Set<Long> joinedPlayers,
        Map<Long, Integer> answers) {

    TriviaRound withAnswer(long userId, int choiceIndex) {
        LinkedHashMap<Long, Integer> next = new LinkedHashMap<>(answers);
        next.put(userId, choiceIndex);
        return new TriviaRound(roundId, gameId, roundNumber, totalRounds,
                messageId, channelId, question,
                shuffledChoices, correctIndex, joinedPlayers,
                Collections.unmodifiableMap(next));
    }

    TriviaRound withMessage(long messageId, long channelId) {
        return new TriviaRound(roundId, gameId, roundNumber, totalRounds,
                messageId, channelId, question,
                shuffledChoices, correctIndex, joinedPlayers, answers);
    }

    boolean allJoinedAnswered() {
        return answers.size() >= joinedPlayers.size();
    }

    String correctAnswerLabel() {
        return shuffledChoices.get(correctIndex);
    }
}
