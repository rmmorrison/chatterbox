package ca.ryanmorrison.chatterbox.config;

/**
 * Startup configuration, read once from the environment.
 *
 * <p>Note that {@code CHATTERBOX_LOG_LEVEL} is deliberately absent: logback
 * reads that variable directly (see {@code logback.xml}), so carrying it here
 * as well only created a field nothing ever read.
 */
public record Config(
        String discordToken,
        boolean devMode,
        DatabaseConfig database,
        HttpConfig http) {

    /**
     * Overridden because the generated record toString would print the bot
     * token verbatim. This record is embedded in the InitContext handed to
     * every module, so a single {@code log.debug("ctx={}", ctx)} in any module
     * — including a third-party one — would leak it.
     */
    @Override
    public String toString() {
        return "Config[discordToken=***, devMode=" + devMode
                + ", database=" + database + ", http=" + http + "]";
    }

    public record DatabaseConfig(String url, String user, String password) {
        public boolean isPostgres() { return url.startsWith("jdbc:postgresql:"); }
        public boolean isSqlite()   { return url.startsWith("jdbc:sqlite:"); }

        /**
         * Same reasoning as {@link Config#toString()}: never print the
         * password. The URL is masked too, since credentials can be embedded
         * in it as {@code //user:pass@host} rather than passed separately.
         */
        @Override
        public String toString() {
            return "DatabaseConfig[url=" + maskUserInfo(url) + ", user=" + user + ", password=***]";
        }

        private static String maskUserInfo(String url) {
            return url == null ? "" : url.replaceAll("(?i)(//[^/@:]+):[^/@]*@", "$1:***@");
        }
    }

    public record HttpConfig(int port) {}

    public static Config fromEnvironment() {
        return fromEnvironment(System::getenv);
    }

    static Config fromEnvironment(java.util.function.Function<String, String> env) {
        String token = required(env, "CHATTERBOX_DISCORD_TOKEN");
        String dbUrl = required(env, "CHATTERBOX_DB_URL");
        boolean devMode = Boolean.parseBoolean(envOrDefault(env, "CHATTERBOX_DEV_MODE", "false"));
        int httpPort = parsePort(envOrDefault(env, "CHATTERBOX_HTTP_PORT", "8080"));

        var db = new DatabaseConfig(
                dbUrl,
                envOrDefault(env, "CHATTERBOX_DB_USER", ""),
                envOrDefault(env, "CHATTERBOX_DB_PASSWORD", ""));

        return new Config(token, devMode, db, new HttpConfig(httpPort));
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
