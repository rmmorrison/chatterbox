package ca.ryanmorrison.chatterbox.features.isitdown;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.EnumSet;

/**
 * Slash-command entry point for {@code /isitdown}. Validates the URL,
 * defers the reply (the probe takes up to {@link IsItDownChecker#REQUEST_TIMEOUT}s),
 * runs the check, and renders one of the {@link CheckResult} variants as
 * a Discord message. Mentions in the URL text are suppressed so a sneaky
 * input like {@code @everyone} in the URL field can't actually ping anyone.
 */
final class IsItDownHandler extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(IsItDownHandler.class);

    private final IsItDownChecker checker;

    IsItDownHandler(IsItDownChecker checker) {
        this.checker = checker;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!IsItDownModule.COMMAND.equals(event.getName())) return;

        OptionMapping urlOpt = event.getOption(IsItDownModule.OPT_URL);
        String rawUrl = urlOpt == null ? "" : urlOpt.getAsString();

        OptionMapping privateOpt = event.getOption(IsItDownModule.OPT_PRIVATE);
        boolean ephemeral = privateOpt != null && privateOpt.getAsBoolean();

        // URL parsing is cheap; do it before deferring so we can reply with a
        // quick rejection without burning the deferred-reply slot.
        UrlGuard.ParsedUrl parsed = UrlGuard.parse(rawUrl);
        if (parsed instanceof UrlGuard.ParsedUrl.Rejected(String reason)) {
            event.reply(formatResult(rawUrl, new CheckResult.InvalidUrl(reason)))
                    .setEphemeral(true)
                    .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class))
                    .queue();
            return;
        }

        URI uri = ((UrlGuard.ParsedUrl.Ok) parsed).uri();
        event.deferReply(ephemeral).queue();

        UrlGuard.ResolvedUrl resolved = UrlGuard.resolve(uri);
        CheckResult result = switch (resolved) {
            case UrlGuard.ResolvedUrl.Ok ok -> {
                try {
                    yield checker.check(uri);
                } catch (RuntimeException e) {
                    log.warn("Unexpected error checking {}", uri, e);
                    yield new CheckResult.Other(uri.getHost());
                }
            }
            case UrlGuard.ResolvedUrl.DnsFailure(String host) -> new CheckResult.DnsFailure(host);
            case UrlGuard.ResolvedUrl.Disallowed(String reason) -> new CheckResult.Disallowed(reason);
        };

        event.getHook().sendMessage(formatResult(uri.toString(), result))
                .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class))
                .queue();
    }

    /**
     * Renders {@code result} as a Discord message string. The "down for
     * everyone or just me" framing is loosely preserved: when the site is
     * up, we tell the user it's "just you"; when something fails, we point
     * at the cause.
     */
    static String formatResult(String url, CheckResult result) {
        String safeUrl = inlineCode(url);
        return switch (result) {
            case CheckResult.Live(int status, long ms) ->
                    "✅ " + safeUrl + " answered `" + status + "` in " + ms + " ms. "
                            + "(Looks fine — must be just you.)";
            case CheckResult.BadStatus(int status, long ms) ->
                    "⚠️ " + safeUrl + " answered `" + status + "` in " + ms + " ms. "
                            + "(Server's responding, but not happily — not just you.)";
            case CheckResult.DnsFailure(String host) ->
                    "❌ Couldn't resolve `" + sanitiseHost(host) + "`. "
                            + "(Check the spelling, or the host doesn't exist.)";
            case CheckResult.ConnectionRefused(String host) ->
                    "❌ `" + sanitiseHost(host) + "` refused the connection. "
                            + "(Down for everyone, by the looks of it.)";
            case CheckResult.Unreachable(String host) ->
                    "❌ Couldn't reach `" + sanitiseHost(host) + "` — no route to host. "
                            + "(Likely down for everyone.)";
            case CheckResult.TlsError(String host) ->
                    "❌ TLS handshake with `" + sanitiseHost(host) + "` failed. "
                            + "(Bad certificate or protocol mismatch.)";
            case CheckResult.Timeout(int seconds) ->
                    "⏱️ " + safeUrl + " didn't respond within " + seconds + " seconds. "
                            + "(Could be down, or just very slow.)";
            case CheckResult.InvalidUrl(String reason) ->
                    "❌ That doesn't look like a valid HTTP(S) URL: " + reason;
            case CheckResult.Disallowed(String reason) ->
                    "🚫 I won't probe that address: " + reason;
            case CheckResult.Other(String host) ->
                    "❌ Couldn't reach `" + sanitiseHost(host) + "` — unknown error. "
                            + "(Check the bot logs for details.)";
        };
    }

    /** Wrap the URL in inline-code backticks; falls back to plain text if that's unsafe. */
    private static String inlineCode(String url) {
        // Backticks inside the URL would break the inline-code span; if any are
        // present, drop the formatting rather than letting the markdown bleed.
        if (url == null || url.isEmpty()) return "(empty)";
        return url.contains("`") ? url : "`" + url + "`";
    }

    /** Hostnames pulled out of URI.getHost() shouldn't contain backticks, but be safe. */
    private static String sanitiseHost(String host) {
        if (host == null) return "(unknown)";
        return host.replace("`", "");
    }
}
