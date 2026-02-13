package xyz.mcutils.backend.common.renderer.impl.skin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import xyz.mcutils.backend.common.ImageUtils;
import xyz.mcutils.backend.common.RendererUtils;
import xyz.mcutils.backend.common.renderer.RenderOptions;
import xyz.mcutils.backend.common.renderer.SkinRenderer;
import xyz.mcutils.backend.common.renderer.texture.PlayerModelCoordinates;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.service.SkinService;

import java.awt.*;
import java.awt.image.BufferedImage;

@AllArgsConstructor
@Getter
public class BackRenderer extends SkinRenderer {
    public static final BackRenderer INSTANCE = new BackRenderer();

    private static final int LOGICAL_W = 16;
    private static final int LOGICAL_H = 32;

    @Override
    public BufferedImage render(Skin skin, int size, RenderOptions options) {
        byte[] skinBytes = SkinService.INSTANCE.getSkinTexture(skin.getTextureId(), skin.getRawTextureUrl(), true);
        BufferedImage skinImage = ImageUtils.decodeImage(skinBytes);
        boolean slim = skin.getModel() == Skin.Model.SLIM;
        boolean overlays = options.renderOverlays();

        double scale = (double) size / LOGICAL_H;
        int outW = Math.max(1, (int) (LOGICAL_W * scale));
        int outH = Math.max(1, size);
        BufferedImage out = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();

        int leftArmX = slim ? 1 : 0;
        int rightArmX = 12;
        int armW = slim ? 3 : 4;

        drawVanillaPart(g, skinImage, RendererUtils.scaleLogicalCoord(4, scale), RendererUtils.scaleLogicalCoord(0, scale), RendererUtils.scaleLogical(8, scale), RendererUtils.scaleLogical(8, scale),
                PlayerModelCoordinates.Skin.HEAD_BACK, slim, overlays);
        drawVanillaPart(g, skinImage, RendererUtils.scaleLogicalCoord(4, scale), RendererUtils.scaleLogicalCoord(8, scale), RendererUtils.scaleLogical(8, scale), RendererUtils.scaleLogical(12, scale),
                PlayerModelCoordinates.Skin.BODY_BACK, slim, overlays);
        drawVanillaPart(g, skinImage, RendererUtils.scaleLogicalCoord(leftArmX, scale), RendererUtils.scaleLogicalCoord(8, scale), RendererUtils.scaleLogical(armW, scale), RendererUtils.scaleLogical(12, scale),
                PlayerModelCoordinates.Skin.LEFT_ARM_BACK, slim, overlays);
        drawVanillaPart(g, skinImage, RendererUtils.scaleLogicalCoord(rightArmX, scale), RendererUtils.scaleLogicalCoord(8, scale), RendererUtils.scaleLogical(armW, scale), RendererUtils.scaleLogical(12, scale),
                PlayerModelCoordinates.Skin.RIGHT_ARM_BACK, slim, overlays);
        drawVanillaPart(g, skinImage, RendererUtils.scaleLogicalCoord(4, scale), RendererUtils.scaleLogicalCoord(20, scale), RendererUtils.scaleLogical(4, scale), RendererUtils.scaleLogical(12, scale),
                PlayerModelCoordinates.Skin.LEFT_LEG_BACK, slim, overlays);
        drawVanillaPart(g, skinImage, RendererUtils.scaleLogicalCoord(8, scale), RendererUtils.scaleLogicalCoord(20, scale), RendererUtils.scaleLogical(4, scale), RendererUtils.scaleLogical(12, scale),
                PlayerModelCoordinates.Skin.RIGHT_LEG_BACK, slim, overlays);

        g.dispose();
        return out;
    }
}
