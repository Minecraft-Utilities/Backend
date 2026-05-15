package xyz.mcutils.backend.common.renderer.impl.cape;

import lombok.SneakyThrows;
import xyz.mcutils.backend.common.ImageUtils;
import xyz.mcutils.backend.common.math.Vector3;
import xyz.mcutils.backend.common.renderer.RenderOptions;
import xyz.mcutils.backend.common.renderer.Renderer;
import xyz.mcutils.backend.common.renderer.model.impl.PlayerModel;
import xyz.mcutils.backend.common.renderer.raster.Face;
import xyz.mcutils.backend.common.renderer.raster.Isometric3DRenderer;
import xyz.mcutils.backend.common.renderer.raster.Isometric3DRenderer.ViewParams;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
import xyz.mcutils.backend.service.CapeService;

import java.awt.image.BufferedImage;
import java.util.List;

public class VanillaCapeIsoRenderer extends Renderer<VanillaCape> {
    public static final VanillaCapeIsoRenderer INSTANCE = new VanillaCapeIsoRenderer();

    private static final double ASPECT_RATIO = 10.0 / 16.0;
    private static final double PITCH_DEG = -15.0;
    private static final double YAW_DEG = 35.0;
    private static final Vector3 TARGET = new Vector3(0, 16, 0);
    private static final Vector3 EYE = new Vector3(0, 16, -20);

    @Override
    @SneakyThrows
    public BufferedImage render(VanillaCape cape, int size, RenderOptions options) {
        byte[] capeBytes = CapeService.INSTANCE.getCapeTexture(cape);
        BufferedImage capeImage = ImageUtils.decodeImage(capeBytes);
        List<Face> faces = PlayerModel.buildCapeFaces();
        ViewParams view = new ViewParams(EYE, TARGET, YAW_DEG, PITCH_DEG, ASPECT_RATIO);
        return Isometric3DRenderer.INSTANCE.render(capeImage, faces, view, size);
    }
}
