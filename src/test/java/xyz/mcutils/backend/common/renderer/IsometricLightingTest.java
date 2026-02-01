package xyz.mcutils.backend.common.renderer;

import org.junit.jupiter.api.Test;
import xyz.mcutils.backend.common.math.Vector3;
import xyz.mcutils.backend.common.math.Vector3Utils;

import java.io.IOException;
import java.io.InputStream;

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

    @Test
    void fragmentShaderContainsBrightnessFormula() throws IOException {
        String frag;
        try (InputStream in = getClass().getResourceAsStream("/shaders/isometric/gpu.frag")) {
            assertNotNull(in, "Fragment shader not found");
            frag = new String(in.readAllBytes());
        }
        assertTrue(frag.contains("u_minBrightness + (1.0 - u_minBrightness)") && frag.contains("dot(") && frag.contains("u_sunDirection") && frag.contains("* 0.5") && frag.contains("clamp("),
                "Fragment shader must contain canonical brightness formula");
    }
}
