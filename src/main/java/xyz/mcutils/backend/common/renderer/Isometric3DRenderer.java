package xyz.mcutils.backend.common.renderer;

import xyz.mcutils.backend.common.math.Vector3;
import xyz.mcutils.backend.common.renderer.model.Face;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Abstraction for 3D isometric rendering: given textures and faces, implementations
 * rotate by yaw/pitch, orthographically project, depth-sort, and draw quads.
 * Supports multiple textures (e.g. skin 64×64). Used by full-body and head renderers.
 * Implementations may be software (CPU/Graphics2D) or GPU-accelerated.
 */
public interface Isometric3DRenderer {

    /**
     * Renders the given textured face batches with the given view onto an image.
     *
     * @param batches list of (texture, faces) — e.g. skin 64×64
     * @param view    view parameters (eye, target, yaw, pitch, aspect ratio)
     * @param size    output height in pixels; width = size * aspectRatio
     * @return the rendered image
     */
    BufferedImage render(List<TexturedFaces> batches, ViewParams view, int size);

    /**
     * Renders faces with a single texture. Convenience for skin-only rendering.
     *
     * @param texture the texture (e.g. 64×64 skin)
     * @param faces   the list of textured faces
     * @param view    view parameters
     * @param size    output height in pixels
     * @return the rendered image
     */
    default BufferedImage render(BufferedImage texture, List<Face> faces, ViewParams view, int size) {
        return render(List.of(new TexturedFaces(texture, faces)), view, size);
    }

    /**
     * View parameters for the 3D isometric renderer.
     * The target is also used as the model rotation center.
     */
    record ViewParams(Vector3 eye, Vector3 target, double yawDeg,
                      double pitchDeg, double aspectRatio) {}

    /** Pairs a texture with its faces. Used for multi-texture rendering. */
    record TexturedFaces(BufferedImage texture, List<Face> faces) {}
}
