package ca.ryanmorrison.chatterbox.config;

public record Config(
        String discordToken,
        boolean devMode,
        DatabaseConfig database,
        HttpConfig http,
        String logLevel) {

    public record DatabaseConfig(String url, String user, String password) {
        public boolean isPostgres() { return url.startsWith("jdbc:postgresql:"); }
        public boolean isSqlite()   { return url.startsWith("jdbc:sqlite:"); }
    }

    public record HttpConfig(int port) {}

    public static Config fromEnvironment() {
        return fromEnvironment(System::getenv);
    }

    static Config fromEnvironment(java.util.function.Function<String, String> env) {
        String token = required(env, "CHATTERBOX_DISCORD_TOKEN");
        String dbUrl = required(env, "CHATTERBOX_DB_URL");
        boolean devMode = Boolean.parseBoolean(envOrDefault(env, "CHATTERBOX_DEV_MODE", "false"));
        String logLevel = envOrDefault(env, "CHATTERBOX_LOG_LEVEL", "INFO");
        int httpPort = parsePort(envOrDefault(env, "CHATTERBOX_HTTP_PORT", "8080"));

        var db = new DatabaseConfig(
                dbUrl,
                envOrDefault(env, "CHATTERBOX_DB_USER", ""),
                envOrDefault(env, "CHATTERBOX_DB_PASSWORD", ""));

        return new Config(token, devMode, db, new HttpConfig(httpPort), logLevel);
    }

    private static int parsePort(String raw) {
        try {
            int p = Integer.parseInt(raw);
            if (p < 1 || p > 65535) {
                throw new IllegalStateException("CHATTERBOX_HTTP_PORT must be 1-65535, got " + p);
            }
            return p;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("CHATTERBOX_HTTP_PORT is not a valid integer: " + raw, e);
        }
    }

    private static String required(java.util.function.Function<String, String> env, String key) {
        String v = env.apply(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException(key + " is required.");
        }
        return v;
    }

    private static String envOrDefault(java.util.function.Function<String, String> env, String key, String fallback) {
        String v = env.apply(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
