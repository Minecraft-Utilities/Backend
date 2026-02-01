package xyz.mcutils.backend.common.renderer;

import xyz.mcutils.backend.common.math.Vector3;
import xyz.mcutils.backend.common.math.Vector3Utils;

/**
 * Canonical sun-based directional lighting for isometric 3D rendering.
 * Used by both software and GPU renderers; keep in sync with isometric.frag.
 */
public final class IsometricLighting {

    /** Minimum face brightness (ambient floor); range [0, 1]. */
    public static final double MIN_BRIGHTNESS = 0.78;

    /** Sun direction in world space (normalized). Default: top-right-front. */
    public static final Vector3 SUN_DIRECTION = Vector3Utils.normalize(new Vector3(1, 1, 0.5));

    /**
     * GLSL expression for brightness in fragment shader.
     * Used for parity verification; must match isometric.frag.
     */
    public static final String BRIGHTNESS_GLSL =
            "clamp(u_minBrightness + (1.0 - u_minBrightness) * (1.0 + dot(normalize(v_normal), u_sunDirection)) * 0.5, 0.0, 1.0)";

    private IsometricLighting() {}

    /**
     * Computes brightness for a face based on its normal and sun direction.
     *
     * @param normal       world-space face normal (will be normalized)
     * @param sunDirection sun direction in world space (should be normalized)
     * @param minBrightness minimum brightness floor [0, 1]
     * @return brightness in [0, 1]
     */
    public static double computeBrightness(Vector3 normal, Vector3 sunDirection, double minBrightness) {
        Vector3 n = Vector3Utils.normalize(normal);
        double dot = Vector3Utils.dot(n, sunDirection);
        return Math.max(0, Math.min(1, minBrightness + (1.0 - minBrightness) * (1.0 + dot) * 0.5));
    }
}
