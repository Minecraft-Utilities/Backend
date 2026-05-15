package xyz.mcutils.backend.common.renderer.model.impl;

import xyz.mcutils.backend.common.math.Vector3;
import xyz.mcutils.backend.common.math.Vector3Utils;
import xyz.mcutils.backend.common.renderer.model.ModelUtils;
import xyz.mcutils.backend.common.renderer.raster.Face;
import xyz.mcutils.backend.common.renderer.texture.CapeModelCoordinates;
import xyz.mcutils.backend.common.renderer.texture.PlayerModelCoordinates;
import xyz.mcutils.backend.model.domain.skin.Skin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minecraft player model for software 3D rendering.
 * Coordinates: Y up, front at -Z, pos is min corner.
 */
public class PlayerModel {
    private static final Map<FaceCacheKey, List<Face>> FACE_CACHE = new ConcurrentHashMap<>();

    /**
     * Builds all faces for the player model.
     *
     * @param skin           the skin (determines slim/classic arms)
     * @param renderOverlays whether to include the overlay layer
     * @return the list of textured faces (unmodifiable, may be shared)
     */
    public static List<Face> buildFaces(Skin skin, boolean renderOverlays) {
        boolean slim = skin.getModel() == Skin.Model.SLIM;
        return FACE_CACHE.computeIfAbsent(new FaceCacheKey(slim, renderOverlays), cacheKey -> {
            List<Face> faces = new ArrayList<>();

            // Base layer: head, body, left arm, right arm, left leg, right leg
            ModelUtils.addBox(faces, -4, 24, -4, 8, 8, 8, ModelUtils.uvFrom(PlayerModelCoordinates.ModelBox.HEAD.getBaseUv(slim)));
            ModelUtils.addBox(faces, -4, 12, -2, 8, 12, 4, ModelUtils.uvFrom(PlayerModelCoordinates.ModelBox.BODY.getBaseUv(slim)));
            ModelUtils.addBox(faces, slim ? -7 : -8, 12, -2, slim ? 3 : 4, 12, 4, ModelUtils.uvFrom(PlayerModelCoordinates.ModelBox.LEFT_ARM.getBaseUv(slim)));
            ModelUtils.addBox(faces, 4, 12, -2, slim ? 3 : 4, 12, 4, ModelUtils.uvFrom(PlayerModelCoordinates.ModelBox.RIGHT_ARM.getBaseUv(slim)));
            ModelUtils.addBox(faces, -4, 0, -2, 4, 12, 4, ModelUtils.uvFrom(PlayerModelCoordinates.ModelBox.LEFT_LEG.getBaseUv(slim)));
            ModelUtils.addBox(faces, 0, 0, -2, 4, 12, 4, ModelUtils.uvFrom(PlayerModelCoordinates.ModelBox.RIGHT_LEG.getBaseUv(slim)));

            if (cacheKey.renderOverlays()) {
                // Overlay layer: slightly larger boxes with second-layer UVs
                ModelUtils.addBox(faces, -4.5, 23.5, -4.5, 9, 9, 9, ModelUtils.uvFrom(PlayerModelCoordinates.ModelBox.HEAD.getOverlayUv(slim)));
                ModelUtils.addBox(faces, -4.25, 11.75, -2.25, 8.5, 12.5, 4.5, ModelUtils.uvFrom(PlayerModelCoordinates.ModelBox.BODY.getOverlayUv(slim)));
                ModelUtils.addBox(faces, slim ? -7.25 : -8.25, 11.75, -2.25, slim ? 3.5 : 4.5, 12.5, 4.5, ModelUtils.uvFrom(PlayerModelCoordinates.ModelBox.LEFT_ARM.getOverlayUv(slim)));
                ModelUtils.addBox(faces, 3.75, 11.75, -2.25, slim ? 3.5 : 4.5, 12.5, 4.5, ModelUtils.uvFrom(PlayerModelCoordinates.ModelBox.RIGHT_ARM.getOverlayUv(slim)));
                ModelUtils.addBox(faces, -4.25, -0.25, -2.25, 4.5, 12.5, 4.5, ModelUtils.uvFrom(PlayerModelCoordinates.ModelBox.LEFT_LEG.getOverlayUv(slim)));
                ModelUtils.addBox(faces, -0.25, -0.25, -2.25, 4.5, 12.5, 4.5, ModelUtils.uvFrom(PlayerModelCoordinates.ModelBox.RIGHT_LEG.getOverlayUv(slim)));
            }
            return Collections.unmodifiableList(faces);
        });
    }

    private record FaceCacheKey(boolean slim, boolean renderOverlays) {}

    /**
     * Builds the cape faces. The cape box is positioned flush against the body's back face.
     * In this renderer the camera is on the -Z side, so the player's back is the -Z face
     * (z=pz=-2). The cape occupies z=-3 to z=-2 (1 unit behind the back face),
     * centered on the body (x=-5→5), hanging from the shoulders (y=8→24).
     *
     * <p>UV note: {@code addBox} places {@code uvs[1]} on the -Z face and {@code uvs[0]} on
     * the +Z face.  For a cape, the outer-decorative surface must face -Z (toward the camera
     * in the front view), so we swap slots [0] and [1] before the call.</p>
     *
     * @return an unmodifiable list of cape faces using cape-texture UV coordinates
     */
    public static List<Face> buildCapeFaces() {
        List<Face> faces = new ArrayList<>();
        double[][] raw = ModelUtils.uvFrom(CapeModelCoordinates.ModelBox.CAPE.getUv());
        // Swap [0] (outer-decorative) and [1] (inner-lining) so addBox places the
        // outer-decorative texture on the -Z face (facing the camera in the front view).
        double[][] uvs = new double[][]{raw[1], raw[0], raw[2], raw[3], raw[4], raw[5]};
        ModelUtils.addBox(faces, -5, 8, -3, 10, 16, 1, uvs);
        // Tilt the cape +5° around the top attachment edge (y=24, z=-2): the bottom swings
        // toward more-negative z (away from the body, toward the camera at z=-45).
        Vector3 capePivot = new Vector3(0, 24, -2);
        List<Face> tilted = new ArrayList<>(faces.size());
        for (Face face : faces) {
            tilted.add(rotateFaceX(face, 5.0, capePivot));
        }
        return Collections.unmodifiableList(tilted);
    }

    private static Face rotateFaceX(Face f, double deg, Vector3 pivot) {
        return new Face(
                rotateVertexX(f.v0(), deg, pivot),
                rotateVertexX(f.v1(), deg, pivot),
                rotateVertexX(f.v2(), deg, pivot),
                rotateVertexX(f.v3(), deg, pivot),
                f.u0(), f.v0_(), f.u1(), f.v1_(),
                Vector3Utils.rotateX(f.normal(), deg)
        );
    }

    private static Vector3 rotateVertexX(Vector3 v, double deg, Vector3 pivot) {
        return Vector3Utils.rotateX(v.subtract(pivot), deg).add(pivot);
    }
}
