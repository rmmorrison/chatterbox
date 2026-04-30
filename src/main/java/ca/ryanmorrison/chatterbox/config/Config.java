package ca.ryanmorrison.chatterbox.config;

import java.util.Optional;

public record Config(
        String discordToken,
        boolean devMode,
        Optional<DatabaseConfig> database,
        String logLevel) {

    public record DatabaseConfig(String url, String user, String password) {
        public boolean isPostgres() { return url.startsWith("jdbc:postgresql:"); }
        public boolean isSqlite()   { return url.startsWith("jdbc:sqlite:"); }
    }

    public static Config fromEnvironment() {
        return fromEnvironment(System::getenv);
    }

    static Config fromEnvironment(java.util.function.Function<String, String> env) {
        String token = env.apply("CHATTERBOX_DISCORD_TOKEN");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("CHATTERBOX_DISCORD_TOKEN is required.");
        }
        boolean devMode = Boolean.parseBoolean(envOrDefault(env, "CHATTERBOX_DEV_MODE", "false"));
        String logLevel = envOrDefault(env, "CHATTERBOX_LOG_LEVEL", "INFO");

        Optional<DatabaseConfig> db = Optional.empty();
        String dbUrl = env.apply("CHATTERBOX_DB_URL");
        if (dbUrl != null && !dbUrl.isBlank()) {
            db = Optional.of(new DatabaseConfig(
                    dbUrl,
                    envOrDefault(env, "CHATTERBOX_DB_USER", ""),
                    envOrDefault(env, "CHATTERBOX_DB_PASSWORD", "")));
        }

        return new Config(token, devMode, db, logLevel);
    }

    private static String envOrDefault(java.util.function.Function<String, String> env, String key, String fallback) {
        String v = env.apply(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
