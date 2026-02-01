package xyz.mcutils.backend.common.renderer;

import xyz.mcutils.backend.common.math.Vector3;

/**
 * View parameters for the generic 3D isometric renderer.
 * The target is also used as the model rotation center.
 */
public record ViewParams(
        Vector3 eye,
        Vector3 target,
        double yawDeg,
        double pitchDeg,
        double aspectRatio
) {}
