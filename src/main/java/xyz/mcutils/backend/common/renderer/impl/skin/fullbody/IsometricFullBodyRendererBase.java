package xyz.mcutils.backend.common.renderer.impl.skin.fullbody;

import lombok.SneakyThrows;
import xyz.mcutils.backend.common.math.Vector3;
import xyz.mcutils.backend.common.renderer.*;
import xyz.mcutils.backend.model.skin.ISkinPart;
import xyz.mcutils.backend.model.skin.Skin;
import xyz.mcutils.backend.service.SkinService;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Renders a full Minecraft player body using the generic 3D isometric pipeline.
 * Coordinates loading/normalizing the skin, building full-body faces, and delegating
 * to {@link Isometric3DRenderer} with view params for FRONT or BACK.
 */
public class IsometricFullBodyRendererBase {
    public static final IsometricFullBodyRendererBase INSTANCE = new IsometricFullBodyRendererBase();

    private static final double ASPECT_RATIO = 512.0 / 869.0;
    private static final double YAW_DEG = 45.0;
    private static final double PITCH_DEG = 35.0;
    private static final Vector3 EYE = new Vector3(0, 28, -45);
    private static final Vector3 TARGET = new Vector3(0, 16.5, 0);

    @SneakyThrows
    public BufferedImage render(Skin skin, ISkinPart.Custom part, Side side, boolean renderOverlays, int size) {
        byte[] skinBytes = SkinService.INSTANCE.getSkinImage(skin, true);
        BufferedImage skinImage = SkinImageLoader.load64x64(skinBytes);

        List<Face> faces = PlayerModel.buildFaces(skin, renderOverlays);
        double yaw = YAW_DEG + (side == Side.BACK ? 180.0 : 0.0);
        ViewParams view = new ViewParams(EYE, TARGET, yaw, PITCH_DEG, ASPECT_RATIO);

        return Isometric3DRenderer.render(skinImage, faces, view, size);
    }

    /**
     * The side of the body to render.
     */
    public enum Side {
        FRONT,
        BACK
    }
}
