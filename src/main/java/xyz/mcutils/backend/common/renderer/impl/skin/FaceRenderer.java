package xyz.mcutils.backend.common.renderer.impl.skin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import xyz.mcutils.backend.common.ImageUtils;
import xyz.mcutils.backend.common.renderer.RenderOptions;
import xyz.mcutils.backend.common.renderer.SkinRenderer;
import xyz.mcutils.backend.common.renderer.texture.PlayerModelCoordinates;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.service.SkinService;

import java.awt.*;
import java.awt.image.BufferedImage;

@AllArgsConstructor
@Getter
public class FaceRenderer extends SkinRenderer {
    public static final FaceRenderer INSTANCE = new FaceRenderer();

    @Override
    public BufferedImage render(Skin skin, int size, RenderOptions options) {
        byte[] skinBytes = SkinService.INSTANCE.getSkinTexture(skin.getTextureId(), skin.getRawTextureUrl(), true);
        BufferedImage skinImage = ImageUtils.decodeImage(skinBytes);
        boolean overlays = options.renderOverlays();

        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        drawVanillaPart(g, skinImage, 0, 0, size, size,
                PlayerModelCoordinates.Skin.FACE, false, overlays);
        g.dispose();
        return out;
    }
}
