package ca.ryanmorrison.chatterbox.common.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Reads an HTTP response body with a hard ceiling, enforced <em>while</em>
 * streaming.
 *
 * <p>Exists because the obvious spelling is wrong in a way that looks right:
 * {@code BodyHandlers.ofByteArray()} buffers the entire response first, so
 * checking {@code body.length} afterwards enforces nothing. A server that
 * streams gigabytes exhausts the heap long before the check runs — and several
 * of these endpoints are reached with a user-supplied URL.
 *
 * <p>Pair with {@code BodyHandlers.ofInputStream()}.
 */
public final class BoundedBody {

    private BoundedBody() {}

    /** Signals that the body exceeded {@code maxBytes}; the read is abandoned at that point. */
    public static final class TooLargeException extends IOException {
        private final int maxBytes;

        public TooLargeException(int maxBytes) {
            super("response body exceeded " + maxBytes + " bytes");
            this.maxBytes = maxBytes;
        }

        public int maxBytes() { return maxBytes; }

        /** Convenience for user-facing messages, which quote the cap in KB. */
        public int maxKilobytes() { return maxBytes / 1024; }
    }

    /**
     * Reads {@code in} fully, or throws as soon as more than {@code maxBytes}
     * have arrived.
     *
     * @throws TooLargeException if the body exceeds the cap
     */
    public static byte[] read(InputStream in, int maxBytes) throws IOException {
        byte[] buf = new byte[8192];
        var out = new ByteArrayOutputStream();
        int total = 0;
        int n;
        while ((n = in.read(buf)) >= 0) {
            total += n;
            if (total > maxBytes) throw new TooLargeException(maxBytes);
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
