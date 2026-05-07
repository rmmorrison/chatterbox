package ca.ryanmorrison.chatterbox.config.runtime;

/**
 * Where a resolved config value came from. Surfaced in {@code /config list} /
 * {@code /config get} so admins can see whether a value is per-server, falling
 * back to the deployment-wide env var, or just the built-in default.
 */
public enum ConfigSource {
    GUILD_OVERRIDE("server"),
    ENV_VAR("env"),
    DEFAULT("default");

    private final String label;

    ConfigSource(String label) { this.label = label; }

    public String label() { return label; }
}
