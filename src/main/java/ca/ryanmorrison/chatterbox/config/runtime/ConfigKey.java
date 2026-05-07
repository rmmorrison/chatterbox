package ca.ryanmorrison.chatterbox.config.runtime;

import java.util.Objects;

/**
 * Descriptor for one runtime-overridable configuration value.
 *
 * <p>Modules contribute keys via {@code Module#configKeys()}; the bootstrap
 * unions them into a {@link ConfigRegistry}. Lookups go through
 * {@link RuntimeConfig}, which resolves in the order: per-guild override,
 * environment variable, default.
 *
 * @param key          short dotted name shown to admins in {@code /config}
 *                     (e.g. {@code "autoshorten.enabled"})
 * @param envVar       environment variable that supplies the global fallback
 *                     value (e.g. {@code "CHATTERBOX_AUTOSHORTEN_ENABLED"})
 * @param defaultRaw   default raw value used when neither a per-guild
 *                     override nor the env var is set
 * @param type         typed parser used to validate user input on set and to
 *                     interpret raw strings on read
 * @param description  one-line human-readable explanation, surfaced in
 *                     {@code /config list} and {@code /config get}
 */
public record ConfigKey<T>(
        String key,
        String envVar,
        String defaultRaw,
        ConfigType<T> type,
        String description) {

    public ConfigKey {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(envVar, "envVar");
        Objects.requireNonNull(defaultRaw, "defaultRaw");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(description, "description");
        // Validate the default at construction time so a misconfigured module
        // fails loudly at startup rather than at first /config get.
        try {
            type.parse(defaultRaw);
        } catch (ConfigType.InvalidValueException e) {
            throw new IllegalArgumentException(
                    "Invalid default for config key '" + key + "': " + e.getMessage(), e);
        }
    }

    /** Default value parsed via {@link #type()}. */
    public T defaultValue() {
        return type.parse(defaultRaw);
    }

    public static ConfigKey<Boolean> bool(String key, String envVar, String defaultRaw, String description) {
        return new ConfigKey<>(key, envVar, defaultRaw, ConfigType.BOOLEAN, description);
    }

    public static ConfigKey<Integer> positiveInt(String key, String envVar, String defaultRaw, String description) {
        return new ConfigKey<>(key, envVar, defaultRaw, ConfigType.POSITIVE_INTEGER, description);
    }
}
