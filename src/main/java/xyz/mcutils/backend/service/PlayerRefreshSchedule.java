package xyz.mcutils.backend.service;

import java.time.Duration;
import java.time.Instant;

/**
 * Computes per-player refresh intervals from change velocity and popularity.
 * All state is persisted on {@code players}; selection queries only read {@code next_refresh_at}.
 */
public final class PlayerRefreshSchedule {

    public static final Duration BASE_INTERVAL = Duration.ofHours(3);
    public static final Duration MIN_INTERVAL = Duration.ofMinutes(20);
    public static final Duration MAX_INTERVAL = Duration.ofHours(24);
    public static final Duration FAILURE_BACKOFF = Duration.ofMinutes(30);

    private static final double HALF_LIFE_HOURS = 48.0;
    private static final double VELOCITY_WEIGHT = 2.0;
    private static final double VIEW_WEIGHT = 0.3;
    private static final double MAX_VELOCITY = 10.0;

    private PlayerRefreshSchedule() {
    }

    /**
     * Decays stored velocity by elapsed time since the last refresh, then bumps on detected changes.
     */
    public static double updateVelocity(double currentVelocity, Instant lastUpdated, Instant now, boolean hadChanges) {
        double hoursSince = Duration.between(lastUpdated, now).toMillis() / 3_600_000.0;
        if (hoursSince < 0) {
            hoursSince = 0;
        }
        double velocity = currentVelocity * Math.pow(0.5, hoursSince / HALF_LIFE_HOURS);
        if (hadChanges) {
            velocity += 1.0;
        }
        return Math.min(velocity, MAX_VELOCITY);
    }

    /**
     * Maps activity (velocity + popularity) to a refresh interval between {@link #MIN_INTERVAL} and {@link #MAX_INTERVAL}.
     * Zero activity yields {@link #MAX_INTERVAL}; high activity approaches {@link #MIN_INTERVAL}.
     */
    public static Duration intervalFor(double velocity, long monthlyViews) {
        double activity = VELOCITY_WEIGHT * velocity + VIEW_WEIGHT * Math.log1p(monthlyViews);
        long span = MAX_INTERVAL.toMillis() - MIN_INTERVAL.toMillis();
        long intervalMs = (long) (span / (1.0 + activity) + MIN_INTERVAL.toMillis());
        intervalMs = Math.clamp(intervalMs, MIN_INTERVAL.toMillis(), MAX_INTERVAL.toMillis());
        return Duration.ofMillis(intervalMs);
    }

    public static Instant computeNextRefreshAt(double velocity, long monthlyViews, Instant now) {
        return now.plus(intervalFor(velocity, monthlyViews));
    }
}
