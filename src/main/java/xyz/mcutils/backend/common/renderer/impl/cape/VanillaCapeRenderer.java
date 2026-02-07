package xyz.mcutils.backend.common.renderer.impl.cape;

import lombok.SneakyThrows;
import xyz.mcutils.backend.common.ImageUtils;
import xyz.mcutils.backend.common.renderer.RenderOptions;
import xyz.mcutils.backend.common.renderer.Renderer;
import xyz.mcutils.backend.common.renderer.texture.CapeModelCoordinates;
import xyz.mcutils.backend.common.renderer.texture.Coordinates;
import xyz.mcutils.backend.model.cape.impl.VanillaCape;
import xyz.mcutils.backend.service.CapeService;

import java.awt.image.BufferedImage;

public class VanillaCapeRenderer extends Renderer<VanillaCape> {
    public static final VanillaCapeRenderer INSTANCE = new VanillaCapeRenderer();

    @Override
    @SneakyThrows
    public BufferedImage render(VanillaCape input, int size, RenderOptions options) {
        byte[] capeBytes = CapeService.INSTANCE.getCapeTexture(input);
        BufferedImage capeImage = CapeService.INSTANCE.getCapeImage(capeBytes);

        CapeModelCoordinates.Vanilla capeFront = CapeModelCoordinates.Vanilla.CAPE_FRONT;
        Coordinates coords = capeFront.getCoordinates();
        BufferedImage front = capeImage.getSubimage(coords.x(), coords.y(), capeFront.getWidth(), capeFront.getHeight());
        double scale = (double) size / capeFront.getHeight();
        return ImageUtils.resize(front, scale);
    }
}
