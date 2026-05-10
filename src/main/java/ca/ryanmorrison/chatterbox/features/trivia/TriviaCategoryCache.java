package ca.ryanmorrison.chatterbox.features.trivia;

import ca.ryanmorrison.chatterbox.features.trivia.dto.CategoryListResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Lazy in-memory cache of opentdb's category list.
 *
 * <p>opentdb's category numbering is stable (the same set of ids has
 * shipped for years) and the list is short, so a one-shot fetch on
 * first use is enough — no expiry, no refresh. The first call to
 * {@link #categories()} from <em>any</em> path triggers the network
 * request; concurrent callers race on a synchronized block and only
 * one fetch runs.
 *
 * <p>Failures are not cached: a fetch that throws leaves the cache
 * unset so a subsequent call retries. This keeps a transient outage
 * during the autocomplete on the very first {@code /trivia} of the bot's
 * life from permanently blanking the suggestion list.
 */
final class TriviaCategoryCache {

    private static final Logger log = LoggerFactory.getLogger(TriviaCategoryCache.class);

    private final TriviaClient client;
    private volatile List<CategoryListResponse.Category> cached;

    TriviaCategoryCache(TriviaClient client) {
        this.client = client;
    }

    /**
     * Categories in opentdb's display order. Returns an empty list if a
     * fetch attempt fails — autocomplete callers degrade to "no
     * suggestions" rather than throwing.
     */
    List<CategoryListResponse.Category> categories() {
        List<CategoryListResponse.Category> local = cached;
        if (local != null) return local;
        synchronized (this) {
            if (cached != null) return cached;
            try {
                cached = List.copyOf(client.fetchCategories());
                return cached;
            } catch (TriviaClient.TriviaException e) {
                log.debug("Trivia categories fetch failed, will retry on next call: {}",
                        e.getMessage());
                return List.of();
            } catch (RuntimeException e) {
                log.warn("Unexpected error fetching trivia categories", e);
                return List.of();
            }
        }
    }

    /**
     * Resolve {@code id} → display name. Empty if the id isn't in the
     * cached list (or the cache hasn't been populated yet); callers
     * render a generic "Category #N" fallback in that case.
     */
    Optional<String> nameFor(int id) {
        for (CategoryListResponse.Category c : categories()) {
            if (c.id() == id) return Optional.ofNullable(c.name());
        }
        return Optional.empty();
    }
}
