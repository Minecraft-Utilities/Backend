package xyz.mcutils.backend.common.renderer;

import lombok.AllArgsConstructor;
import lombok.Getter;
import xyz.mcutils.backend.common.math.Vector3;

/**
 * A textured quad with 4 vertices and UV coordinates for software 3D rendering.
 */
@AllArgsConstructor
@Getter
public class Face {
    private final Vector3 v0, v1, v2, v3;
    private final double u0, v0_, u1, v1_;
}
