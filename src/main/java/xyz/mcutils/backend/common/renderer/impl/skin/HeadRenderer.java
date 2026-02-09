package xyz.mcutils.backend.common.renderer.impl.skin;

import lombok.SneakyThrows;
import xyz.mcutils.backend.common.ImageUtils;
import xyz.mcutils.backend.common.math.Vector3;
import xyz.mcutils.backend.common.renderer.RenderOptions;
import xyz.mcutils.backend.common.renderer.SkinRenderer;
import xyz.mcutils.backend.common.renderer.model.impl.PlayerHeadModel;
import xyz.mcutils.backend.common.renderer.raster.Face;
import xyz.mcutils.backend.common.renderer.raster.Isometric3DRenderer;
import xyz.mcutils.backend.common.renderer.raster.Isometric3DRenderer.ViewParams;
import xyz.mcutils.backend.model.domain.skin.Skin;
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
    public BufferedImage render(Skin skin, int size, RenderOptions options) {
        return render(skin, size, options, YAW_DEG, PITCH_DEG);
    }

    /**
     * Renders the head with custom view angles for better overlay visibility.
     *
     * @param skin     the skin
     * @param size     output height in pixels
     * @param options  rendering options (e.g. overlay layer)
     * @param yawDeg   view yaw in degrees
     * @param pitchDeg view pitch in degrees
     * @return the rendered image
     */
    @SneakyThrows
    public BufferedImage render(Skin skin, int size, RenderOptions options, double yawDeg, double pitchDeg) {
        byte[] skinBytes = SkinService.INSTANCE.getSkinTexture(skin.getTextureId(), skin.getTextureUrl(), true);
        BufferedImage skinImage = ImageUtils.decodeImage(skinBytes);

        List<Face> faces = PlayerHeadModel.buildFaces(skin, options.renderOverlays());
        ViewParams view = new ViewParams(HEAD_EYE, HEAD_TARGET, yawDeg, pitchDeg, ASPECT_RATIO);
        return Isometric3DRenderer.INSTANCE.render(skinImage, faces, view, size);
    }
}
