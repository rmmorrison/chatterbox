package ca.ryanmorrison.chatterbox.features.trivia;

import java.util.Locale;

/**
 * Search filter passed to {@link TriviaClient#fetch}. Both fields are
 * optional — null means "no constraint".
 *
 * @param categoryId numeric Open Trivia DB category id (see
 *                   {@link TriviaCategories}); {@code null} for any.
 * @param difficulty {@code "easy"}, {@code "medium"}, {@code "hard"}, or
 *                   {@code null} for any.
 */
record TriviaFilter(Integer categoryId, String difficulty) {

    static TriviaFilter any() {
        return new TriviaFilter(null, null);
    }

    /**
     * Validate user-facing inputs. Returns a normalised filter (lowercased
     * difficulty) or throws if anything is bogus.
     */
    static TriviaFilter validated(Integer categoryId, String difficulty) throws TriviaClient.TriviaException {
        String d = null;
        if (difficulty != null && !difficulty.isBlank()) {
            d = difficulty.trim().toLowerCase(Locale.ROOT);
            if (!d.equals("easy") && !d.equals("medium") && !d.equals("hard")) {
                throw new TriviaClient.TriviaException("Difficulty must be easy, medium, or hard.");
            }
        }
        if (categoryId != null && !TriviaCategories.isKnown(categoryId)) {
            throw new TriviaClient.TriviaException("Unknown category.");
        }
        return new TriviaFilter(categoryId, d);
    }
}
