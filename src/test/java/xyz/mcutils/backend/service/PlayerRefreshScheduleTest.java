package xyz.mcutils.backend.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerRefreshScheduleTest {

    private static final Instant BASE = Instant.parse("2026-01-01T12:00:00Z");

    @Test
    void stablePlayerGetsMaxInterval() {
        Duration interval = PlayerRefreshSchedule.intervalFor(0, 0);
        assertEquals(PlayerRefreshSchedule.MAX_INTERVAL, interval);
    }

    @Test
    void volatilePlayerGetsShorterInterval() {
        Duration stable = PlayerRefreshSchedule.intervalFor(0, 0);
        Duration volatilePlayer = PlayerRefreshSchedule.intervalFor(5, 0);
        assertTrue(volatilePlayer.compareTo(stable) < 0);
    }

    @Test
    void popularPlayerGetsShorterIntervalThanColdStable() {
        Duration cold = PlayerRefreshSchedule.intervalFor(0, 0);
        Duration popular = PlayerRefreshSchedule.intervalFor(0, 10_000);
        assertTrue(popular.compareTo(cold) < 0);
    }

    @Test
    void velocityBumpsOnChange() {
        double velocity = PlayerRefreshSchedule.updateVelocity(0, BASE, BASE, true);
        assertEquals(1.0, velocity, 0.001);
    }

    @Test
    void velocityDecaysWithoutChange() {
        Instant later = BASE.plus(Duration.ofHours(48));
        double velocity = PlayerRefreshSchedule.updateVelocity(4, BASE, later, false);
        assertEquals(2.0, velocity, 0.001);
    }

    @Test
    void velocityIsCapped() {
        double velocity = PlayerRefreshSchedule.updateVelocity(9.5, BASE, BASE, true);
        assertEquals(10.0, velocity, 0.001);
    }

    @Test
    void hotThresholdVelocityHalvesTheIntervalSpan() {
        // At HOT_VELOCITY_THRESHOLD the velocity contribution to activity is exactly 1.0,
        // so the interval span (MAX - MIN) is halved: ~12.2h vs the 24h stable baseline.
        // This is the property that makes the hot refresh tier worth prioritizing.
        long span = PlayerRefreshSchedule.MAX_INTERVAL.toMillis() - PlayerRefreshSchedule.MIN_INTERVAL.toMillis();
        Duration hot = PlayerRefreshSchedule.intervalFor(PlayerRefreshSchedule.HOT_VELOCITY_THRESHOLD, 0);
        assertEquals(span / 2 + PlayerRefreshSchedule.MIN_INTERVAL.toMillis(), hot.toMillis());
    }

    @Test
    void intervalRespectsMinimumClamp() {
        Duration extreme = PlayerRefreshSchedule.intervalFor(10, 1_000_000);
        assertTrue(extreme.compareTo(PlayerRefreshSchedule.MIN_INTERVAL) >= 0);
        assertTrue(extreme.compareTo(PlayerRefreshSchedule.intervalFor(1, 0)) < 0);
    }

    @Test
    void computeNextRefreshAtIsNowPlusInterval() {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        Instant next = PlayerRefreshSchedule.computeNextRefreshAt(2, 500, now);
        Duration expected = PlayerRefreshSchedule.intervalFor(2, 500);
        assertEquals(now.plus(expected), next);
    }
}
