package xyz.mcutils.backend.service;

import java.time.Duration;
import java.time.Instant;

/**
 * Computes per-player refresh intervals from change velocity and popularity.
 * All state is persisted on {@code players}; selection queries only read {@code next_refresh_at}.
 * Intervals are spaced geometrically between {@link #MIN_INTERVAL} and {@link #IDLE_INTERVAL}, so the
 * refresh rate tracks the change rate across orders of magnitude instead of a flat hourly range.
 */
public final class PlayerRefreshSchedule {

    public static final Duration BASE_INTERVAL = Duration.ofHours(3);
    public static final Duration MIN_INTERVAL = Duration.ofMinutes(20);
    /** Ceiling for a player with no observed activity. */
    public static final Duration IDLE_INTERVAL = Duration.ofDays(14);
    public static final Duration FAILURE_BACKOFF = Duration.ofMinutes(30);
    /**
     * Re-check a player shortly after a detected change: a player who just changed is far
     * likelier to change again in the next minutes-to-hours (skin tweaks, name-drop sprees),
     * and the adaptive cadence alone would not revisit them for hours. Self-terminating:
     * once a burst check comes back clean, scheduling falls back to the adaptive interval.
     */
    public static final Duration BURST_INTERVAL = Duration.ofMinutes(20);

    private static final double HALF_LIFE_HOURS = 48.0;
    private static final double VELOCITY_WEIGHT = 2.0;
    private static final double VIEW_WEIGHT = 0.05;
    private static final double CHANGE_BUMP = 2.0;
    private static final double MAX_VELOCITY = 30.0;
    /** Activity at which a player lands on the geometric midpoint of the interval range. */
    private static final double ACTIVITY_HALF = 1.0;
    private static final double MIN_OVER_IDLE = (double) MIN_INTERVAL.toMillis() / IDLE_INTERVAL.toMillis();

    /**
     * Minimum change velocity for a player to join the "hot" refresh tier (claimed before the
     * cold tier, ordered by velocity descending).
     * <p>
     * MUST stay in sync with the partial index predicate in
     * {@code V39__hot_refresh_priority_index.sql} and the JPQL literals in
     * {@code PlayerRepository.findHotDueForRefreshSkippable} /
     * {@code findColdDueForRefreshSkippable} (kept as literals so Postgres can use the partial index).
     */
    public static final double HOT_VELOCITY_THRESHOLD = 0.5;

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
            velocity += CHANGE_BUMP;
        }
        return Math.min(velocity, MAX_VELOCITY);
    }

    /**
     * Interval until the next refresh: a short burst right after any detected change,
     * otherwise the adaptive cadence derived from velocity and popularity.
     */
    public static Duration nextRefreshInterval(boolean hadChanges, double velocity, long monthlyViews) {
        return hadChanges ? BURST_INTERVAL : intervalFor(velocity, monthlyViews);
    }

    /**
     * Maps activity (velocity + popularity) to a refresh interval between {@link #MIN_INTERVAL} and {@link #IDLE_INTERVAL}.
     * Zero activity yields {@link #IDLE_INTERVAL}; the interval shrinks geometrically as activity rises.
     */
    public static Duration intervalFor(double velocity, long monthlyViews) {
        double activity = VELOCITY_WEIGHT * velocity + VIEW_WEIGHT * Math.log1p(monthlyViews);
        double hotness = activity / (activity + ACTIVITY_HALF);
        long intervalMs = (long) (IDLE_INTERVAL.toMillis() * Math.pow(MIN_OVER_IDLE, hotness));
        return Duration.ofMillis(Math.clamp(intervalMs, MIN_INTERVAL.toMillis(), IDLE_INTERVAL.toMillis()));
    }

    public static Instant computeNextRefreshAt(double velocity, long monthlyViews, Instant now) {
        return now.plus(intervalFor(velocity, monthlyViews));
    }
}
