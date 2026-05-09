package ca.ryanmorrison.chatterbox.features.trivia;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Hard-coded Open Trivia DB category list. The IDs are stable — opentdb
 * has shipped the same category numbering since 2016 — so we don't bother
 * fetching {@code /api_category.php} at startup. Kept in display order so
 * the slash-command choices read like a menu.
 *
 * <p>Slash-command choice limit is 25; we have 24 categories, leaving one
 * slot of slack (the "Any" choice is the absent option, not an explicit
 * entry).
 */
final class TriviaCategories {

    /** Display-name → opentdb id, in the order shown to users. */
    private static final Map<String, Integer> BY_NAME = build();

    private static Map<String, Integer> build() {
        var m = new LinkedHashMap<String, Integer>();
        m.put("General Knowledge", 9);
        m.put("Books", 10);
        m.put("Film", 11);
        m.put("Music", 12);
        m.put("Musicals & Theatres", 13);
        m.put("Television", 14);
        m.put("Video Games", 15);
        m.put("Board Games", 16);
        m.put("Science & Nature", 17);
        m.put("Computers", 18);
        m.put("Mathematics", 19);
        m.put("Mythology", 20);
        m.put("Sports", 21);
        m.put("Geography", 22);
        m.put("History", 23);
        m.put("Politics", 24);
        m.put("Art", 25);
        m.put("Celebrities", 26);
        m.put("Animals", 27);
        m.put("Vehicles", 28);
        m.put("Comics", 29);
        m.put("Gadgets", 30);
        m.put("Anime & Manga", 31);
        m.put("Cartoons & Animations", 32);
        return m;
    }

    private TriviaCategories() {}

    /** Display order, with stable iteration. */
    static Map<String, Integer> all() {
        return BY_NAME;
    }

    static boolean isKnown(int id) {
        return BY_NAME.values().contains(id);
    }

    static Set<Integer> ids() {
        return Set.copyOf(BY_NAME.values());
    }
}
