package ca.ryanmorrison.chatterbox.config.runtime;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the one guarantee that was lost by moving the config load out of
 * {@code computeIfAbsent}.
 *
 * <p>Holding the map's bin lock across the load used to make invalidation
 * race-free for free: {@code cache.remove} could not slip between a stale read
 * and its put. Loading outside the lock reopens that window, so a version
 * counter closes it. This test drives the interleaving deterministically with
 * a latch rather than hoping a thread race reproduces — a timing-based version
 * of this test passes even with the guard removed, which is worse than no test.
 */
class RuntimeConfigCachePublishTest {

    private static final long GUILD = 42L;
    private static final long ADMIN = 7L;

    private static final ConfigKey<Integer> THRESHOLD = ConfigKey.positiveInt(
            "autoshorten.threshold", "CHATTERBOX_AUTOSHORTEN_THRESHOLD", "160", "Min URL length.");

    @Test
    void aLoadThatStartedBeforeAWriteDoesNotPublishOverIt() throws Exception {
        var repo = mock(RuntimeConfigRepository.class);
        var loadStarted = new CountDownLatch(1);
        var writeDone = new CountDownLatch(1);

        // First load blocks until the write has completed, then returns the
        // value that was current when it began. Later loads see the new value.
        when(repo.findAllForGuild(GUILD))
                .thenAnswer(invocation -> {
                    loadStarted.countDown();
                    assertTrue(writeDone.await(5, TimeUnit.SECONDS), "write never completed");
                    return Map.of("autoshorten.threshold", "160");
                })
                .thenReturn(Map.of("autoshorten.threshold", "999"));

        var registry = new ConfigRegistry(List.of(THRESHOLD));
        var runtime = new RuntimeConfig(registry, repo, name -> null,
                Clock.fixed(java.time.Instant.EPOCH, ZoneOffset.UTC));

        var reader = new Thread(() -> runtime.integer(GUILD, THRESHOLD), "stale-reader");
        reader.start();
        assertTrue(loadStarted.await(5, TimeUnit.SECONDS), "load never started");

        // The write lands while that load is still in flight.
        runtime.set(GUILD, THRESHOLD, "999", ADMIN);
        writeDone.countDown();
        reader.join(5_000);

        // Without the version guard the in-flight load publishes 160 back into
        // the cache here, and this read returns the overwritten value.
        assertEquals(999, runtime.integer(GUILD, THRESHOLD),
                "a load that began before the write published stale config over it");
    }

    @Test
    void anUncontendedLoadStillPopulatesTheCache() {
        var repo = mock(RuntimeConfigRepository.class);
        when(repo.findAllForGuild(GUILD)).thenReturn(Map.of("autoshorten.threshold", "321"));

        var registry = new ConfigRegistry(List.of(THRESHOLD));
        var runtime = new RuntimeConfig(registry, repo, name -> null,
                Clock.fixed(java.time.Instant.EPOCH, ZoneOffset.UTC));

        assertEquals(321, runtime.integer(GUILD, THRESHOLD));
        assertEquals(321, runtime.integer(GUILD, THRESHOLD));

        // Second read must come from the cache, not the repository.
        org.mockito.Mockito.verify(repo, org.mockito.Mockito.times(1)).findAllForGuild(GUILD);
    }

    @Test
    void writesInvalidateSoTheNextReadSeesThem() {
        var repo = mock(RuntimeConfigRepository.class);
        when(repo.findAllForGuild(GUILD))
                .thenReturn(Map.of("autoshorten.threshold", "100"))
                .thenReturn(Map.of("autoshorten.threshold", "200"));
        when(repo.delete(anyLong(), anyString())).thenReturn(true);

        var registry = new ConfigRegistry(List.of(THRESHOLD));
        var runtime = new RuntimeConfig(registry, repo, name -> null,
                Clock.fixed(java.time.Instant.EPOCH, ZoneOffset.UTC));

        assertEquals(100, runtime.integer(GUILD, THRESHOLD));
        runtime.set(GUILD, THRESHOLD, "200", ADMIN);
        assertEquals(200, runtime.integer(GUILD, THRESHOLD));
    }

    @Test
    void aFailingRepositoryDegradesToDefaultsInsteadOfThrowing() {
        var repo = mock(RuntimeConfigRepository.class);
        when(repo.findAllForGuild(anyLong()))
                .thenThrow(new org.jooq.exception.DataAccessException("pool exhausted"));

        var registry = new ConfigRegistry(List.of(THRESHOLD));
        var runtime = new RuntimeConfig(registry, repo, name -> null,
                Clock.fixed(java.time.Instant.EPOCH, ZoneOffset.UTC));

        // This runs on a JDA listener thread in production; throwing here kills
        // message handling for the whole guild.
        assertEquals(160, runtime.integer(GUILD, THRESHOLD));
    }
}
