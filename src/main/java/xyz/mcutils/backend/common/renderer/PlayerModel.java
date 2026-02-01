package xyz.mcutils.backend.common.renderer;

import xyz.mcutils.backend.model.skin.ISkinPart;
import xyz.mcutils.backend.model.skin.Skin;

import java.util.ArrayList;
import java.util.List;

/**
 * Minecraft player model for software 3D rendering.
 * Coordinates: Y up, front at -Z, pos is min corner.
 */
public class PlayerModel {

    /**
     * Builds all faces for the player model.
     *
     * @param skin           the skin (determines slim/classic arms)
     * @param renderOverlays whether to include the overlay layer
     * @return the list of textured faces
     */
    public static List<Face> buildFaces(Skin skin, boolean renderOverlays) {
        List<Face> faces = new ArrayList<>();
        boolean slim = skin.getModel() == Skin.Model.SLIM;

        // Base layer: head, body, left arm, right arm, left leg, right leg
        ModelUtils.addBox(faces, -4, 24, -4, 8, 8, 8, ModelUtils.uvFrom(ISkinPart.Vanilla.ModelBox.HEAD.getBaseUv(slim)));
        ModelUtils.addBox(faces, -4, 12, -2, 8, 12, 4, ModelUtils.uvFrom(ISkinPart.Vanilla.ModelBox.BODY.getBaseUv(slim)));
        ModelUtils.addBox(faces, slim ? -7 : -8, 12, -2, slim ? 3 : 4, 12, 4, ModelUtils.uvFrom(ISkinPart.Vanilla.ModelBox.LEFT_ARM.getBaseUv(slim)));
        ModelUtils.addBox(faces, 4, 12, -2, slim ? 3 : 4, 12, 4, ModelUtils.uvFrom(ISkinPart.Vanilla.ModelBox.RIGHT_ARM.getBaseUv(slim)));
        ModelUtils.addBox(faces, -4, 0, -2, 4, 12, 4, ModelUtils.uvFrom(ISkinPart.Vanilla.ModelBox.LEFT_LEG.getBaseUv(slim)));
        ModelUtils.addBox(faces, 0, 0, -2, 4, 12, 4, ModelUtils.uvFrom(ISkinPart.Vanilla.ModelBox.RIGHT_LEG.getBaseUv(slim)));

        if (renderOverlays && !skin.isLegacy()) {
            // Overlay layer: slightly larger boxes with second-layer UVs
            ModelUtils.addBox(faces, -4.5, 23.5, -4.5, 9, 9, 9, ModelUtils.uvFrom(ISkinPart.Vanilla.ModelBox.HEAD.getOverlayUv(slim)));
            ModelUtils.addBox(faces, -4.25, 11.75, -2.25, 8.5, 12.5, 4.5, ModelUtils.uvFrom(ISkinPart.Vanilla.ModelBox.BODY.getOverlayUv(slim)));
            ModelUtils.addBox(faces, slim ? -7.25 : -8.25, 11.75, -2.25, slim ? 3.5 : 4.5, 12.5, 4.5, ModelUtils.uvFrom(ISkinPart.Vanilla.ModelBox.LEFT_ARM.getOverlayUv(slim)));
            ModelUtils.addBox(faces, 3.75, 11.75, -2.25, slim ? 3.5 : 4.5, 12.5, 4.5, ModelUtils.uvFrom(ISkinPart.Vanilla.ModelBox.RIGHT_ARM.getOverlayUv(slim)));
            ModelUtils.addBox(faces, -4.25, -0.25, -2.25, 4.5, 12.5, 4.5, ModelUtils.uvFrom(ISkinPart.Vanilla.ModelBox.LEFT_LEG.getOverlayUv(slim)));
            ModelUtils.addBox(faces, -0.25, -0.25, -2.25, 4.5, 12.5, 4.5, ModelUtils.uvFrom(ISkinPart.Vanilla.ModelBox.RIGHT_LEG.getOverlayUv(slim)));
        }

        return faces;
    }
}
