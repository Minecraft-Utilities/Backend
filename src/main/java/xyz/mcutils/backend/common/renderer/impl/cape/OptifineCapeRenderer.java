package xyz.mcutils.backend.common.renderer.impl.cape;

import lombok.SneakyThrows;
import xyz.mcutils.backend.common.ImageUtils;
import xyz.mcutils.backend.common.renderer.RenderOptions;
import xyz.mcutils.backend.common.renderer.Renderer;
import xyz.mcutils.backend.common.renderer.texture.CapeModelCoordinates;
import xyz.mcutils.backend.common.renderer.texture.Coordinates;
import xyz.mcutils.backend.model.domain.cape.impl.OptifineCape;
import xyz.mcutils.backend.service.CapeService;

import java.awt.image.BufferedImage;

public class OptifineCapeRenderer extends Renderer<OptifineCape> {
    public static final OptifineCapeRenderer INSTANCE = new OptifineCapeRenderer();

    @Override
    @SneakyThrows
    public BufferedImage render(OptifineCape input, int size, RenderOptions options) {
        byte[] capeBytes = CapeService.INSTANCE.getCapeTexture(input);
        BufferedImage capeImage = CapeService.INSTANCE.getCapeImage(capeBytes);

        // todo: needs to be improved, but it'll do for now
        boolean legacy = capeImage.getWidth() == 46 || capeImage.getHeight() == 22;

        CapeModelCoordinates.Optifine capeFront = CapeModelCoordinates.Optifine.CAPE_FRONT;
        Coordinates coords = legacy ? capeFront.getLegacyCoordinates() : capeFront.getCoordinates();
        int width = coords.width();
        int height = coords.height();

        BufferedImage front = capeImage.getSubimage(coords.x(), coords.y(), width, height);
        double scale = (double) size / height;
        return ImageUtils.resize(front, scale);
    }
}
