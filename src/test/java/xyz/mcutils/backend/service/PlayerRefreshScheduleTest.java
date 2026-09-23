package xyz.mcutils.backend.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerRefreshScheduleTest {

    private static final Instant BASE = Instant.parse("2026-01-01T12:00:00Z");

    @Test
    void neverChangingPlayerGetsIdleInterval() {
        assertEquals(PlayerRefreshSchedule.IDLE_INTERVAL, PlayerRefreshSchedule.intervalFor(0, 0));
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
        assertEquals(2.0, velocity, 0.001);
    }

    @Test
    void velocityDecaysWithoutChange() {
        Instant later = BASE.plus(Duration.ofHours(48));
        double velocity = PlayerRefreshSchedule.updateVelocity(4, BASE, later, false);
        assertEquals(2.0, velocity, 0.001);
    }

    @Test
    void velocityIsCapped() {
        double velocity = PlayerRefreshSchedule.updateVelocity(29.5, BASE, BASE, true);
        assertEquals(30.0, velocity, 0.001);
    }

    @Test
    void hotThresholdVelocitySitsAtTheGeometricMidpoint() {
        // At HOT_VELOCITY_THRESHOLD the interval is the geometric mean of the range (~10.6h).
        long midpoint = (long) Math.sqrt((double) PlayerRefreshSchedule.MIN_INTERVAL.toMillis()
                * PlayerRefreshSchedule.IDLE_INTERVAL.toMillis());
        Duration hot = PlayerRefreshSchedule.intervalFor(PlayerRefreshSchedule.HOT_VELOCITY_THRESHOLD, 0);
        assertEquals(midpoint, hot.toMillis(), 5);
        assertTrue(PlayerRefreshSchedule.IDLE_INTERVAL.toMillis() / midpoint >= 30);
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

    @Test
    void changedPlayerIsScheduledForBurstRecheck() {
        Duration interval = PlayerRefreshSchedule.nextRefreshInterval(true, 0, 0);
        assertEquals(PlayerRefreshSchedule.BURST_INTERVAL, interval);
    }

    @Test
    void cleanBurstFallsBackToAdaptiveInterval() {
        double velocity = 3.0;
        long views = 500;
        assertEquals(
                PlayerRefreshSchedule.intervalFor(velocity, views),
                PlayerRefreshSchedule.nextRefreshInterval(false, velocity, views)
        );
    }

    @Test
    void highVelocityDrivesIntervalWellBeneathOldFloor() {
        Duration interval = PlayerRefreshSchedule.intervalFor(30, 0);
        assertTrue(interval.compareTo(Duration.ofMinutes(60)) < 0);
        assertTrue(interval.compareTo(PlayerRefreshSchedule.MIN_INTERVAL) >= 0);
    }

    @Test
    void hotTierIsOrdersOfMagnitudeFasterThanTheIdleTier() {
        Duration idle = PlayerRefreshSchedule.intervalFor(0, 0);
        Duration hot = PlayerRefreshSchedule.intervalFor(30, 0);
        assertTrue(idle.dividedBy(hot) >= 500, "idle=" + idle + " hot=" + hot);
    }

    @Test
    void moreChangesBuyMoreRefreshes() {
        double hourly = refreshesPerDay(1);
        double weekly = refreshesPerDay(1 / 7.0);
        double monthly = refreshesPerDay(1 / 30.0);
        double never = refreshesPerDay(0);
        assertTrue(hourly > weekly * 5, "hourly=" + hourly + " weekly=" + weekly);
        assertTrue(weekly > monthly * 2, "weekly=" + weekly + " monthly=" + monthly);
        assertTrue(monthly > never * 5, "monthly=" + monthly + " never=" + never);
        assertTrue(hourly > never * 100, "hourly=" + hourly + " never=" + never);
    }

    @Test
    void quietPlayerFadesBackToTheIdleTier() {
        double velocity = PlayerRefreshSchedule.updateVelocity(0, BASE, BASE, true);
        Duration interval = PlayerRefreshSchedule.nextRefreshInterval(true, velocity, 0);
        Instant now = BASE;
        Instant horizon = BASE.plus(Duration.ofDays(30));
        int refreshes = 0;
        while (now.isBefore(horizon)) {
            Instant previous = now;
            now = now.plus(interval);
            refreshes++;
            velocity = PlayerRefreshSchedule.updateVelocity(velocity, previous, now, false);
            interval = PlayerRefreshSchedule.nextRefreshInterval(false, velocity, 0);
        }
        assertTrue(interval.compareTo(Duration.ofDays(13)) >= 0, "interval=" + interval);
        assertTrue(refreshes <= 60, "a single change cost " + refreshes + " refreshes");
    }

    /**
     * Refreshes per day the schedule converges on for a player whose profile changes
     * {@code changesPerDay} times, feeding each computed interval back in as the loop does.
     */
    private static double refreshesPerDay(double changesPerDay) {
        double velocity = 0;
        double changeDebt = 0;
        int refreshes = 0;
        Instant now = BASE;
        Instant horizon = BASE.plus(Duration.ofDays(365));
        Duration interval = PlayerRefreshSchedule.BASE_INTERVAL;
        while (now.isBefore(horizon)) {
            Instant previous = now;
            now = now.plus(interval);
            refreshes++;
            changeDebt += changesPerDay * Duration.between(previous, now).toMillis() / (double) Duration.ofDays(1).toMillis();
            boolean changed = changeDebt >= 1.0;
            if (changed) {
                changeDebt -= 1.0;
            }
            velocity = PlayerRefreshSchedule.updateVelocity(velocity, previous, now, changed);
            interval = PlayerRefreshSchedule.nextRefreshInterval(changed, velocity, 0);
        }
        return refreshes / 365.0;
    }
}
