package xyz.mcutils.backend.common.renderer;

import org.junit.jupiter.api.Test;
import xyz.mcutils.backend.common.math.Vector3;
import xyz.mcutils.backend.common.math.Vector3Utils;

import static org.junit.jupiter.api.Assertions.*;

class IsometricLightingTest {

    @Test
    void computeBrightness_matchesExpectedValues() {
        Vector3 sunDir = IsometricLighting.SUN_DIRECTION;
        double minBright = IsometricLighting.MIN_BRIGHTNESS;

        // Face fully toward sun (normal = sun direction): dot = 1, brightness = 1
        assertEquals(1.0, IsometricLighting.computeBrightness(sunDir, sunDir, minBright), 1e-6);

        // Face fully away from sun (normal = -sun direction): dot = -1, brightness = minBright
        assertEquals(minBright, IsometricLighting.computeBrightness(sunDir.multiply(-1), sunDir, minBright), 1e-6);

        // Face perpendicular: dot = 0, brightness = minBright + (1-minBright)*0.5
        Vector3 perp = Vector3Utils.normalize(new Vector3(-sunDir.getZ(), 0, sunDir.getX()));
        double expected = minBright + (1.0 - minBright) * 0.5;
        assertEquals(expected, IsometricLighting.computeBrightness(perp, sunDir, minBright), 1e-6);
    }
}
