package xyz.mcutils.backend.common.renderer;

import lombok.extern.slf4j.Slf4j;
import xyz.mcutils.backend.model.domain.skin.Skin;

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
}
