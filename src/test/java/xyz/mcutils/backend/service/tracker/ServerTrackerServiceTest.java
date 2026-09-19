package xyz.mcutils.backend.service.tracker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link ServerTrackerService.HoneypotSurgeGuard}: the sliding-window honeypot
 * verdicts that auto-pause harvesting once the flagged ratio crosses the threshold.
 */
class ServerTrackerServiceTest {

    @Test
    void pausesAfterThresholdFlaggedServers() {
        ServerTrackerService.HoneypotSurgeGuard guard = new ServerTrackerService.HoneypotSurgeGuard(3, 2, 300);
        assertFalse(guard.report(true), "first flagged server is under the threshold");
        assertTrue(guard.report(true), "second flagged server crosses the threshold and arms the pause");
        assertTrue(guard.paused());
    }

    @Test
    void pauseIsSetAndResetOnceArmed() {
        ServerTrackerService.HoneypotSurgeGuard guard = new ServerTrackerService.HoneypotSurgeGuard(3, 2, 300);
        guard.report(true);
        assertFalse(guard.paused());
        guard.report(true);
        assertTrue(guard.paused(), "once armed, the pause holds until the cooldown expires");

        // Even clean verdicts after arming do not un-pause; the window must slide below the threshold.
        guard.report(false);
        guard.report(false);
        guard.report(false);
        assertTrue(guard.paused());
    }

    @Test
    void cleanVerdictsNeverPause() {
        ServerTrackerService.HoneypotSurgeGuard guard = new ServerTrackerService.HoneypotSurgeGuard(3, 2, 300);
        for (int i = 0; i < 10; i++) {
            assertFalse(guard.report(false));
        }
        assertFalse(guard.paused());
    }
}