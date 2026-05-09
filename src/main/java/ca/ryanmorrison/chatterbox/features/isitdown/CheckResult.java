package ca.ryanmorrison.chatterbox.features.isitdown;

/**
 * Outcome of a single {@code /isitdown} probe.
 *
 * <p>One variant per failure category. The handler maps each variant to a
 * dedicated user-facing message; raw exception details never leak. A failure
 * we don't recognise is reported as {@link Other} with a generic message
 * (the underlying exception is logged at WARN inside
 * {@link IsItDownChecker} so an operator can still diagnose the cause).
 */
sealed interface CheckResult {

    /** {@code 2xx} (or post-redirect {@code 2xx}) response. The site is responding normally. */
    record Live(int status, long elapsedMs) implements CheckResult {}

    /** Server responded but with a non-2xx final status (after following any redirects). */
    record BadStatus(int status, long elapsedMs) implements CheckResult {}

    /** Hostname couldn't be resolved (or only resolved to denied addresses — same user-facing meaning). */
    record DnsFailure(String host) implements CheckResult {}

    /** TCP connection actively refused by the host. */
    record ConnectionRefused(String host) implements CheckResult {}

    /** No route to host / network unreachable. */
    record Unreachable(String host) implements CheckResult {}

    /** TLS handshake failed (expired / self-signed / mismatched cert, protocol mismatch, etc.). */
    record TlsError(String host) implements CheckResult {}

    /** Request didn't complete within the configured timeout. */
    record Timeout(int seconds) implements CheckResult {}

    /** Pre-flight URL validation rejected the input. */
    record InvalidUrl(String reason) implements CheckResult {}

    /**
     * The URL resolves to an address we refuse to probe (loopback, private,
     * link-local, etc.). Surfaced separately from {@link InvalidUrl} so the
     * user-facing message can explain it's a policy decision, not a typo.
     */
    record Disallowed(String reason) implements CheckResult {}

    /** Catch-all for I/O failures we didn't classify. Original cause is logged, never surfaced. */
    record Other(String host) implements CheckResult {}
}
