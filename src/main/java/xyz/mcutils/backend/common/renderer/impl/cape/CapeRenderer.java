package xyz.mcutils.backend.common.renderer.impl.cape;

import lombok.SneakyThrows;
import xyz.mcutils.backend.common.ImageUtils;
import xyz.mcutils.backend.common.renderer.Renderer;
import xyz.mcutils.backend.common.renderer.texture.CapeModelCoordinates;
import xyz.mcutils.backend.model.cape.Cape;
import xyz.mcutils.backend.service.CapeService;

import java.awt.image.BufferedImage;

public class CapeRenderer extends Renderer<Cape> {
    public static final CapeRenderer INSTANCE = new CapeRenderer();

    @Override @SneakyThrows
    public BufferedImage render(Cape input, int size) {
        byte[] capeBytes = CapeService.INSTANCE.getCapeTexture(input);
        BufferedImage capeImage = CapeService.INSTANCE.getCapeImage(capeBytes);

        CapeModelCoordinates.Vanilla capeFront = CapeModelCoordinates.Vanilla.CAPE_FRONT;
        CapeModelCoordinates.Vanilla.Coordinates coords = capeFront.getCoordinates();
        BufferedImage front = capeImage.getSubimage(coords.x(), coords.y(), capeFront.getWidth(), capeFront.getHeight());
        double scale = (double) size / capeFront.getHeight();
        return ImageUtils.resize(front, scale);
    }
}
