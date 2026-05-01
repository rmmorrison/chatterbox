package ca.ryanmorrison.chatterbox.features.autoreply;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatchdogCharSequenceTest {

    @Test
    void delegatesNormalAccess() {
        var w = WatchdogCharSequence.wrap("hello", 1000);
        assertEquals(5, w.length());
        assertEquals('h', w.charAt(0));
        assertEquals("hello", w.toString());
    }

    @Test
    void subSequenceShareDeadline() {
        var w = WatchdogCharSequence.wrap("hello world", 1000);
        CharSequence sub = w.subSequence(6, 11);
        assertEquals("world", sub.toString());
        assertEquals('w', sub.charAt(0));
    }

    @Test
    void charAtThrowsAfterDeadline() throws InterruptedException {
        // Watchdog with a 1ms budget — the sleep guarantees we're past it before charAt runs.
        var w = WatchdogCharSequence.wrap("hello", 1);
        Thread.sleep(20);
        assertThrows(WatchdogCharSequence.RegexTimeoutException.class, () -> w.charAt(0));
    }

    @Test
    void subSequenceCharAtThrowsAfterDeadline() throws InterruptedException {
        var w = WatchdogCharSequence.wrap("hello world", 1);
        CharSequence sub = w.subSequence(6, 11);
        Thread.sleep(20);
        assertThrows(WatchdogCharSequence.RegexTimeoutException.class, () -> sub.charAt(0));
    }

    @Test
    void wellBehavedRegexCompletesNormally() {
        Pattern pattern = Pattern.compile("(?i)hello");
        var input = WatchdogCharSequence.wrap("HELLO WORLD", 1000);
        assertTrue(pattern.matcher(input).find());
    }
}
