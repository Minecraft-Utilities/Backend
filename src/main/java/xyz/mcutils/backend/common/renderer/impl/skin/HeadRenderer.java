package xyz.mcutils.backend.common.renderer.impl.skin;

import lombok.SneakyThrows;
import xyz.mcutils.backend.common.math.Vector3;
import xyz.mcutils.backend.common.renderer.Isometric3DRenderer;
import xyz.mcutils.backend.common.renderer.Isometric3DRenderer.ViewParams;
import xyz.mcutils.backend.common.renderer.SkinRenderer;
import xyz.mcutils.backend.common.renderer.model.Face;
import xyz.mcutils.backend.common.renderer.model.impl.PlayerHeadModel;
import xyz.mcutils.backend.model.skin.Skin;
import xyz.mcutils.backend.model.skin.SkinPart;
import xyz.mcutils.backend.service.SkinService;

import java.awt.image.BufferedImage;
import java.util.List;

public class HeadRenderer extends SkinRenderer {
    public static final HeadRenderer INSTANCE = new HeadRenderer();

    private static final double PITCH_DEG = 35.0;
    private static final double YAW_DEG = 45.0;
    private static final double ASPECT_RATIO = 1.0;
    /** Head center in model space; eye is along -Z so head fills frame. */
    private static final Vector3 HEAD_TARGET = new Vector3(0, 28, 0);
    private static final Vector3 HEAD_EYE = new Vector3(0, 28, -20);

    @Override
    @SneakyThrows
    public BufferedImage render(Skin skin, SkinPart part, boolean renderOverlays, int size) {
        return render(skin, part, renderOverlays, size, YAW_DEG, PITCH_DEG);
    }

    /**
     * Renders the head with custom view angles for better overlay visibility.
     *
     * @param skin           the skin
     * @param part           the part (unused, for API consistency)
     * @param renderOverlays whether to include overlay layer
     * @param size           output height in pixels
     * @param yawDeg         view yaw in degrees
     * @param pitchDeg       view pitch in degrees
     * @return the rendered image
     */
    @SneakyThrows
    public BufferedImage render(Skin skin, SkinPart part, boolean renderOverlays, int size,
                                double yawDeg, double pitchDeg) {
        byte[] skinBytes = SkinService.INSTANCE.getSkinBytes(skin, true);
        BufferedImage skinImage = SkinService.getSkinImage(skinBytes);

        List<Face> faces = PlayerHeadModel.buildFaces(skin, renderOverlays);
        ViewParams view = new ViewParams(HEAD_EYE, HEAD_TARGET, yawDeg, pitchDeg, ASPECT_RATIO);
        return Isometric3DRenderer.render(skinImage, faces, view, size);
    }
}
