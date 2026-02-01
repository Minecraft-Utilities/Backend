package xyz.mcutils.backend.common.renderer.impl.skin.fullbody;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import xyz.mcutils.backend.common.math.Vector3;
import xyz.mcutils.backend.common.renderer.Isometric3DRenderer;
import xyz.mcutils.backend.common.renderer.Isometric3DRendererBackend;
import xyz.mcutils.backend.common.renderer.Isometric3DRenderer.ViewParams;
import xyz.mcutils.backend.common.renderer.model.Face;
import xyz.mcutils.backend.common.renderer.model.impl.PlayerModel;
import xyz.mcutils.backend.model.skin.Skin;
import xyz.mcutils.backend.model.skin.SkinPart;
import xyz.mcutils.backend.service.SkinService;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Renders a full Minecraft player body using the generic 3D isometric pipeline.
 * Coordinates loading/normalizing the skin, building full-body faces, and delegating
 * to {@link Isometric3DRenderer} with view params for FRONT or BACK.
 */
@Slf4j
public class FullBodyRendererBase {
    public static final FullBodyRendererBase INSTANCE = new FullBodyRendererBase();

    private static final double ASPECT_RATIO = 512.0 / 869.0;
    private static final double PITCH_DEG = 35.0;
    private static final double YAW_DEG = 45.0;
    private static final Vector3 EYE = new Vector3(0, 28, -45);
    private static final Vector3 TARGET = new Vector3(0, 16.5, 0);

    @SneakyThrows
    public BufferedImage render(Skin skin, SkinPart part, Side side, boolean renderOverlays, int size) {
        return render(skin, part, side, renderOverlays, size, YAW_DEG, PITCH_DEG);
    }

    /**
     * Renders the full body with custom view angles.
     */
    @SneakyThrows
    public BufferedImage render(Skin skin, SkinPart part, Side side, boolean renderOverlays, int size,
                                double yawDeg, double pitchDeg) {
        long tSkin = System.nanoTime();
        byte[] skinBytes = SkinService.INSTANCE.getSkinBytes(skin, true);
        BufferedImage skinImage = SkinService.getSkinImage(skinBytes);
        double tSkinMs = (System.nanoTime() - tSkin) / 1e6;

        long tFaces = System.nanoTime();
        List<Face> faces = PlayerModel.buildFaces(skin, renderOverlays);
        double tFacesMs = (System.nanoTime() - tFaces) / 1e6;

        long tRender = System.nanoTime();
        double yaw = yawDeg + (side == Side.BACK ? 180.0 : 0.0);
        ViewParams view = new ViewParams(EYE, TARGET, yaw, pitchDeg, ASPECT_RATIO);
        BufferedImage result = Isometric3DRendererBackend.get().render(skinImage, faces, view, size);
        double tRenderMs = (System.nanoTime() - tRender) / 1e6;

        if (log.isDebugEnabled()) {
            log.debug("FullBody render: skin={}ms faces={}ms draw={}ms",
                    String.format("%.2f", tSkinMs), String.format("%.2f", tFacesMs), String.format("%.2f", tRenderMs));
        }
        return result;
    }

    /**
     * The side of the body to render.
     */
    public enum Side {
        FRONT,
        BACK
    }
}
