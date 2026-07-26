package ca.ryanmorrison.chatterbox.common.net;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedBodyTest {

    @Test
    void readsABodyUnderTheCap() throws Exception {
        byte[] payload = "hello world".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(payload,
                BoundedBody.read(new ByteArrayInputStream(payload), 1024));
    }

    @Test
    void readsABodyExactlyAtTheCap() throws Exception {
        byte[] payload = new byte[64];
        assertEquals(64, BoundedBody.read(new ByteArrayInputStream(payload), 64).length);
    }

    @Test
    void rejectsABodyOneByteOverTheCap() {
        byte[] payload = new byte[65];
        var e = assertThrows(BoundedBody.TooLargeException.class,
                () -> BoundedBody.read(new ByteArrayInputStream(payload), 64));
        assertEquals(64, e.maxBytes());
    }

    @Test
    void handlesAnEmptyBody() throws Exception {
        assertEquals(0, BoundedBody.read(new ByteArrayInputStream(new byte[0]), 64).length);
    }

    /**
     * The whole point of the class. A post-hoc {@code body.length} check on an
     * already-buffered array cannot do this: the heap is gone before it runs.
     */
    @Test
    void abandonsAnEndlessStreamInsteadOfBufferingItAll() {
        var consumed = new AtomicLong();
        InputStream endless = new InputStream() {
            @Override public int read() {
                consumed.incrementAndGet();
                return 0;
            }
            @Override public int read(byte[] b, int off, int len) {
                consumed.addAndGet(len);
                return len;
            }
        };

        assertThrows(BoundedBody.TooLargeException.class,
                () -> BoundedBody.read(endless, 4096));

        // Bounded work, not "read forever then complain".
        assertTrue(consumed.get() < 64 * 1024,
                () -> "read " + consumed.get() + " bytes from an endless stream");
    }

    @Test
    void propagatesUnderlyingIoFailures() {
        InputStream broken = new InputStream() {
            @Override public int read() throws IOException {
                throw new IOException("connection reset");
            }
        };
        var e = assertThrows(IOException.class, () -> BoundedBody.read(broken, 1024));
        assertEquals("connection reset", e.getMessage());
    }

    @Test
    void reportsTheCapInKilobytesForUserFacingMessages() {
        assertEquals(2048, new BoundedBody.TooLargeException(2 * 1024 * 1024).maxKilobytes());
    }
}
