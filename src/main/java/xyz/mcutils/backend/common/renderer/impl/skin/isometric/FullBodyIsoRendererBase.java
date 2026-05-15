package xyz.mcutils.backend.common.renderer.impl.skin.isometric;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import xyz.mcutils.backend.common.ImageUtils;
import xyz.mcutils.backend.common.math.Vector3;
import xyz.mcutils.backend.common.renderer.model.impl.PlayerModel;
import xyz.mcutils.backend.common.renderer.raster.Face;
import xyz.mcutils.backend.common.renderer.raster.Isometric3DRenderer;
import xyz.mcutils.backend.common.renderer.raster.Isometric3DRenderer.TexturedFaces;
import xyz.mcutils.backend.common.renderer.raster.Isometric3DRenderer.ViewParams;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.service.CapeService;
import xyz.mcutils.backend.service.SkinService;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a full Minecraft player body using the generic 3D isometric pipeline.
 * Coordinates loading/normalizing the skin, building full-body faces, and delegating
 * to {@link Isometric3DRenderer} with view params for FRONT or BACK.
 */
@Slf4j
public class FullBodyIsoRendererBase {
    public static final FullBodyIsoRendererBase INSTANCE = new FullBodyIsoRendererBase();

    private static final double ASPECT_RATIO = 512.0 / 869.0;
    private static final double PITCH_DEG = -20.0;
    private static final double YAW_DEG = 0.0;
    private static final Vector3 EYE = new Vector3(0, 28, -45);
    private static final Vector3 TARGET = new Vector3(0, 16.5, 0);

    @SneakyThrows
    public BufferedImage render(Skin skin, Side side, boolean renderOverlays, int size) {
        return render(skin, null, side, renderOverlays, size, YAW_DEG, PITCH_DEG);
    }

    @SneakyThrows
    public BufferedImage render(Skin skin, @Nullable VanillaCape cape, Side side, boolean renderOverlays, int size) {
        return render(skin, cape, side, renderOverlays, size, YAW_DEG, PITCH_DEG);
    }

    /**
     * Renders the full body with custom view angles.
     */
    @SneakyThrows
    public BufferedImage render(Skin skin, Side side, boolean renderOverlays, int size, double yawDeg, double pitchDeg) {
        return render(skin, null, side, renderOverlays, size, yawDeg, pitchDeg);
    }

    /**
     * Renders the full body with a cape and custom view angles.
     */
    @SneakyThrows
    public BufferedImage render(Skin skin, @Nullable VanillaCape cape, Side side, boolean renderOverlays, int size, double yawDeg, double pitchDeg) {
        byte[] skinBytes = SkinService.INSTANCE.getSkinTexture(skin.getTextureId(), skin.getRawTextureUrl(), true);
        BufferedImage skinImage = ImageUtils.decodeImage(skinBytes);
        List<Face> skinFaces = PlayerModel.buildFaces(skin, renderOverlays);

        double yaw = yawDeg + (side == Side.BACK ? 45.0 : 145.0);
        ViewParams view = new ViewParams(EYE, TARGET, yaw, pitchDeg, ASPECT_RATIO);

        if (cape != null) {
            byte[] capeBytes = CapeService.INSTANCE.getCapeTexture(cape);
            BufferedImage capeImage = ImageUtils.decodeImage(capeBytes);
            List<Face> capeFaces = PlayerModel.buildCapeFaces();
            List<TexturedFaces> batches = new ArrayList<>();
            batches.add(new TexturedFaces(skinImage, skinFaces));
            batches.add(new TexturedFaces(capeImage, capeFaces));
            return Isometric3DRenderer.INSTANCE.render(batches, view, size);
        }

        return Isometric3DRenderer.INSTANCE.render(skinImage, skinFaces, view, size);
    }

    /**
     * The side of the body to render.
     */
    public enum Side {
        FRONT, BACK
    }
}
