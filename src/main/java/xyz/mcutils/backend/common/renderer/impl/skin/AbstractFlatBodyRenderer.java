package xyz.mcutils.backend.common.renderer.impl.skin;

import xyz.mcutils.backend.common.ImageUtils;
import xyz.mcutils.backend.common.RendererUtils;
import xyz.mcutils.backend.common.renderer.RenderOptions;
import xyz.mcutils.backend.common.renderer.SkinRenderer;
import xyz.mcutils.backend.common.renderer.texture.PlayerModelCoordinates;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.service.SkinService;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Shared scaffold for flat (2D) full-body skin renderers.
 * Subclasses supply a {@link PartLayout} that selects the correct texture coordinates
 * and leg-column positions for their side (front or back).
 */
public abstract class AbstractFlatBodyRenderer extends SkinRenderer {
    private static final int LOGICAL_W = 16;
    private static final int LOGICAL_H = 32;

    /**
     * Describes which texture coordinates and leg x-positions to use for a given side.
     *
     * @param head      head texture coordinate
     * @param body      body texture coordinate
     * @param leftArm   left-arm texture coordinate
     * @param rightArm  right-arm texture coordinate
     * @param leftLeg   left-leg texture coordinate
     * @param leftLegX  left-leg output column (in logical pixels)
     * @param rightLeg  right-leg texture coordinate
     * @param rightLegX right-leg output column (in logical pixels)
     */
    protected record PartLayout(
            PlayerModelCoordinates.Skin head,
            PlayerModelCoordinates.Skin body,
            PlayerModelCoordinates.Skin leftArm,
            PlayerModelCoordinates.Skin rightArm,
            PlayerModelCoordinates.Skin leftLeg,
            int leftLegX,
            PlayerModelCoordinates.Skin rightLeg,
            int rightLegX
    ) {}

    protected abstract PartLayout getLayout();

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
        PartLayout layout = getLayout();

        drawVanillaPart(g, skinImage, RendererUtils.scaleLogicalCoord(4, scale),        RendererUtils.scaleLogicalCoord(0, scale),  RendererUtils.scaleLogical(8, scale),    RendererUtils.scaleLogical(8, scale),    layout.head(),     slim, overlays);
        drawVanillaPart(g, skinImage, RendererUtils.scaleLogicalCoord(4, scale),        RendererUtils.scaleLogicalCoord(8, scale),  RendererUtils.scaleLogical(8, scale),    RendererUtils.scaleLogical(12, scale),   layout.body(),     slim, overlays);
        drawVanillaPart(g, skinImage, RendererUtils.scaleLogicalCoord(leftArmX, scale), RendererUtils.scaleLogicalCoord(8, scale),  RendererUtils.scaleLogical(armW, scale), RendererUtils.scaleLogical(12, scale),   layout.leftArm(),  slim, overlays);
        drawVanillaPart(g, skinImage, RendererUtils.scaleLogicalCoord(rightArmX, scale),RendererUtils.scaleLogicalCoord(8, scale),  RendererUtils.scaleLogical(armW, scale), RendererUtils.scaleLogical(12, scale),   layout.rightArm(), slim, overlays);
        drawVanillaPart(g, skinImage, RendererUtils.scaleLogicalCoord(layout.leftLegX(),  scale), RendererUtils.scaleLogicalCoord(20, scale), RendererUtils.scaleLogical(4, scale), RendererUtils.scaleLogical(12, scale), layout.leftLeg(),  slim, overlays);
        drawVanillaPart(g, skinImage, RendererUtils.scaleLogicalCoord(layout.rightLegX(), scale), RendererUtils.scaleLogicalCoord(20, scale), RendererUtils.scaleLogical(4, scale), RendererUtils.scaleLogical(12, scale), layout.rightLeg(), slim, overlays);

        g.dispose();
        return out;
    }
}
