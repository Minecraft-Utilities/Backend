package xyz.mcutils.backend.common.renderer.impl.skin;

import lombok.SneakyThrows;
import xyz.mcutils.backend.common.math.Vector3;
import xyz.mcutils.backend.common.renderer.Isometric3DRenderer;
import xyz.mcutils.backend.common.renderer.SkinRenderer;
import xyz.mcutils.backend.common.renderer.model.Face;
import xyz.mcutils.backend.common.renderer.model.ViewParams;
import xyz.mcutils.backend.common.renderer.model.models.PlayerHeadModel;
import xyz.mcutils.backend.model.skin.ISkinPart;
import xyz.mcutils.backend.model.skin.Skin;
import xyz.mcutils.backend.service.SkinService;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Renders a Minecraft player head using the same 3D isometric pipeline as the
 * full-body renderer, so overlays and depth are handled correctly.
 */
public class IsometricHeadRenderer extends SkinRenderer<ISkinPart.Custom> {
    public static final IsometricHeadRenderer INSTANCE = new IsometricHeadRenderer();

    private static final double PITCH_DEG = 35.0;
    private static final double YAW_DEG = 45.0;
    private static final double ASPECT_RATIO = 1.0;
    /** Head center in model space; eye is along -Z so head fills frame. */
    private static final Vector3 HEAD_TARGET = new Vector3(0, 28, 0);
    private static final Vector3 HEAD_EYE = new Vector3(0, 28, -20);
    private static final ViewParams HEAD_VIEW = new ViewParams(HEAD_EYE, HEAD_TARGET, YAW_DEG, PITCH_DEG, ASPECT_RATIO);

    @Override
    @SneakyThrows
    public BufferedImage render(Skin skin, ISkinPart.Custom part, boolean renderOverlays, int size) {
        byte[] skinBytes = SkinService.INSTANCE.getSkinBytes(skin, true);
        BufferedImage skinImage = SkinService.getSkinImage(skinBytes);

        List<Face> faces = PlayerHeadModel.buildFaces(skin, renderOverlays);
        return Isometric3DRenderer.render(skinImage, faces, HEAD_VIEW, size);
    }
}
