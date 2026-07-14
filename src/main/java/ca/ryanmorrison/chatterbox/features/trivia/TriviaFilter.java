package ca.ryanmorrison.chatterbox.features.trivia;

import java.util.Locale;

/**
 * Search filter passed to {@link TriviaClient#fetchBatch}. Both fields are
 * optional — null means "no constraint".
 *
 * @param categoryId numeric Open Trivia DB category id; {@code null} for
 *                   any. Validation against the live category list is
 *                   delegated to opentdb (it returns response_code 2 for
 *                   bad ids, surfaced as a friendly error).
 * @param difficulty {@code "easy"}, {@code "medium"}, {@code "hard"}, or
 *                   {@code null} for any.
 */
record TriviaFilter(Integer categoryId, String difficulty) {

    static TriviaFilter any() {
        return new TriviaFilter(null, null);
    }

    /**
     * Validate user-facing inputs. Normalises difficulty to lowercase;
     * passes the category id through unchecked (opentdb is the source of
     * truth for which ids are valid, and the autocomplete suggestion
     * list is the user's first line of defence).
     */
    static TriviaFilter validated(Integer categoryId, String difficulty)
            throws TriviaClient.TriviaException {
        String d = null;
        if (difficulty != null && !difficulty.isBlank()) {
            d = difficulty.trim().toLowerCase(Locale.ROOT);
            if (!d.equals("easy") && !d.equals("medium") && !d.equals("hard")) {
                throw new TriviaClient.TriviaException("Difficulty must be easy, medium, or hard.");
            }
        }
        return new TriviaFilter(categoryId, d);
    }
}
