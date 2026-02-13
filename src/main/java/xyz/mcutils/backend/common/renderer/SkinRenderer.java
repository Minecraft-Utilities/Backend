package xyz.mcutils.backend.common.renderer;

import lombok.extern.slf4j.Slf4j;
import xyz.mcutils.backend.common.renderer.texture.Coordinates;
import xyz.mcutils.backend.common.renderer.texture.PlayerModelCoordinates;
import xyz.mcutils.backend.model.domain.skin.Skin;

import java.awt.*;
import java.awt.image.BufferedImage;

@Slf4j
public abstract class SkinRenderer extends Renderer<Skin> {

    /**
     * Renders the skin part for the player's skin (convenience method).
     *
     * @param skin           the player's skin
     * @param renderOverlays whether the overlays should be rendered
     * @param size           the output size (height; width derived per part)
     * @return the rendered skin part
     */
    public BufferedImage render(Skin skin, boolean renderOverlays, int size) {
        return render(skin, size, new RenderOptions(renderOverlays));
    }

    /**
     * Draws a vanilla skin part from the full skin image onto the given graphics at (dx, dy)
     * with destination size (dw, dh). No extra BufferedImage allocations.
     */
    protected static void drawVanillaPart(Graphics2D g, BufferedImage skinImage, int dx, int dy, int dw, int dh,
                                         PlayerModelCoordinates.Skin part, boolean slim, boolean renderOverlays) {
        Coordinates c = part.getCoordinates();
        int sw = c.width();
        if (slim && part.isArm()) sw--;
        int sh = c.height();
        int sx = c.x();
        int sy = c.y();
        g.drawImage(skinImage, dx, dy, dx + dw, dy + dh, sx, sy, sx + sw, sy + sh, null);
        if (renderOverlays && part.getOverlays().length > 0) {
            for (PlayerModelCoordinates.Skin overlay : part.getOverlays()) {
                Coordinates oc = overlay.getCoordinates();
                int ow = oc.width();
                if (slim && overlay.isArm()) ow--;
                g.drawImage(skinImage, dx, dy, dx + dw, dy + dh, oc.x(), oc.y(), oc.x() + ow, oc.y() + oc.height(), null);
            }
        }
    }

}
