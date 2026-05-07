package ca.ryanmorrison.chatterbox.config.runtime;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable registry of every {@link ConfigKey} the bot knows about.
 *
 * <p>Built once at startup from the union of {@code Module#configKeys()}
 * contributions; rejects duplicate {@link ConfigKey#key()} values so two
 * modules can't quietly fight over the same setting. Slash-command
 * autocomplete and {@code /config list} both iterate {@link #all()}, so
 * keys are surfaced in alphabetical order regardless of registration
 * order.
 */
public final class ConfigRegistry {

    private final Map<String, ConfigKey<?>> byKey;

    public ConfigRegistry(Collection<ConfigKey<?>> keys) {
        // LinkedHashMap (wrapped unmodifiable) so callers see iteration order
        // matching alphabetisation; Map.copyOf would lose ordering.
        Map<String, ConfigKey<?>> sorted = new LinkedHashMap<>();
        keys.stream()
                .sorted(Comparator.comparing(ConfigKey::key))
                .forEach(k -> {
                    if (sorted.put(k.key(), k) != null) {
                        throw new IllegalStateException("Duplicate runtime config key: " + k.key());
                    }
                });
        this.byKey = Collections.unmodifiableMap(sorted);
    }

    public Optional<ConfigKey<?>> find(String key) {
        return Optional.ofNullable(byKey.get(key));
    }

    /** All registered keys, alphabetised by {@link ConfigKey#key()}. */
    public List<ConfigKey<?>> all() {
        return List.copyOf(byKey.values());
    }
}
