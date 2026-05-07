package ca.ryanmorrison.chatterbox.config.runtime;

import java.util.function.Function;

/**
 * Typed parser/validator/formatter for a runtime-overridable config value.
 *
 * <p>{@link ConfigKey} stores raw strings (in env vars, in the database, in
 * the slash command's text input) and uses a {@code ConfigType<T>} to
 * convert between those raw strings and the typed value the consumer wants.
 * Parse failures throw {@link InvalidValueException} with a user-facing
 * message that's safe to surface in a Discord reply.
 */
public final class ConfigType<T> {

    public static final ConfigType<Boolean> BOOLEAN = new ConfigType<>(
            "boolean (true / false)",
            raw -> {
                String v = raw.trim().toLowerCase(java.util.Locale.ROOT);
                if (v.equals("true")) return Boolean.TRUE;
                if (v.equals("false")) return Boolean.FALSE;
                throw new InvalidValueException("must be `true` or `false`.");
            },
            Object::toString);

    public static final ConfigType<Integer> POSITIVE_INTEGER = new ConfigType<>(
            "positive integer",
            raw -> {
                int v;
                try {
                    v = Integer.parseInt(raw.trim());
                } catch (NumberFormatException e) {
                    throw new InvalidValueException("must be a positive integer.");
                }
                if (v <= 0) throw new InvalidValueException("must be greater than zero.");
                return v;
            },
            Object::toString);

    private final String label;
    private final Function<String, T> parser;
    private final Function<T, String> formatter;

    private ConfigType(String label, Function<String, T> parser, Function<T, String> formatter) {
        this.label = label;
        this.parser = parser;
        this.formatter = formatter;
    }

    public String label() { return label; }

    public T parse(String raw) {
        if (raw == null) throw new InvalidValueException("value can't be empty.");
        return parser.apply(raw);
    }

    public String format(T value) {
        return formatter.apply(value);
    }

    /** User-facing parse/validation error. The message is shown verbatim in Discord. */
    public static final class InvalidValueException extends RuntimeException {
        public InvalidValueException(String message) { super(message); }
    }
}
