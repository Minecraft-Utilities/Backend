package xyz.mcutils.backend.common.renderer.model.impl;

import xyz.mcutils.backend.common.renderer.model.Face;
import xyz.mcutils.backend.common.renderer.model.ModelUtils;
import xyz.mcutils.backend.model.skin.ISkinPart;
import xyz.mcutils.backend.model.skin.Skin;

import java.util.ArrayList;
import java.util.List;

/**
 * Minecraft player head model for software 3D rendering.
 * Same coordinate system as PlayerModel: Y up, front at -Z.
 */
public class PlayerHeadModel {

    /**
     * Builds faces for the head only (base layer and optional overlay).
     *
     * @param skin           the skin
     * @param renderOverlays whether to include the overlay layer
     * @return the list of textured faces
     */
    public static List<Face> buildFaces(Skin skin, boolean renderOverlays) {
        List<Face> faces = new ArrayList<>();
        boolean slim = skin.getModel() == Skin.Model.SLIM;

        // Base layer: head box at -4, 24, -4, size 8×8×8
        ModelUtils.addBox(faces, -4, 24, -4, 8, 8, 8, ModelUtils.uvFrom(ISkinPart.Vanilla.ModelBox.HEAD.getBaseUv(slim)));

        if (renderOverlays && !skin.isLegacy()) {
            // Overlay layer: slightly larger head box
            ModelUtils.addBox(faces, -4.5, 23.5, -4.5, 9, 9, 9, ModelUtils.uvFrom(ISkinPart.Vanilla.ModelBox.HEAD.getOverlayUv(slim)));
        }

        return faces;
    }
}
