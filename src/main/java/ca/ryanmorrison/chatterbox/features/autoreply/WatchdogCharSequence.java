package ca.ryanmorrison.chatterbox.features.autoreply;

import java.util.concurrent.TimeUnit;

/**
 * A {@link CharSequence} that throws {@link RegexTimeoutException} once a
 * deadline passes. Java's regex engine queries the input via
 * {@link #charAt(int)} on every step, so wrapping the input in this and
 * passing it to {@link java.util.regex.Pattern#matcher(CharSequence)} caps
 * pathological backtracking at the configured timeout.
 *
 * <p>Cheap mitigation against catastrophic regex / ReDoS: doesn't change the
 * regex engine, doesn't restrict syntax. Won't catch every conceivable
 * pathological pattern but it's enough to keep a careless moderator-supplied
 * pattern from pinning a CPU.
 */
final class WatchdogCharSequence implements CharSequence {

    private final CharSequence delegate;
    private final long deadlineNanos;

    static WatchdogCharSequence wrap(CharSequence delegate, long timeoutMillis) {
        return new WatchdogCharSequence(delegate,
                System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis));
    }

    private WatchdogCharSequence(CharSequence delegate, long deadlineNanos) {
        this.delegate = delegate;
        this.deadlineNanos = deadlineNanos;
    }

    @Override
    public int length() { return delegate.length(); }

    @Override
    public char charAt(int index) {
        if (System.nanoTime() > deadlineNanos) throw new RegexTimeoutException();
        return delegate.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        // Reuse the same absolute deadline so the overall budget applies to
        // any sub-views the regex engine may construct internally.
        return new WatchdogCharSequence(delegate.subSequence(start, end), deadlineNanos);
    }

    @Override
    public String toString() { return delegate.toString(); }

    /** Thrown when a regex match exceeds the configured time budget. */
    static final class RegexTimeoutException extends RuntimeException {
        RegexTimeoutException() { super("regex match exceeded time budget"); }
    }
}
