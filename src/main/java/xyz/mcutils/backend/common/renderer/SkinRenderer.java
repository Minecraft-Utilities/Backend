package xyz.mcutils.backend.common.renderer;

import lombok.extern.slf4j.Slf4j;
import xyz.mcutils.backend.model.skin.Skin;
import xyz.mcutils.backend.model.skin.SkinPart;

import java.awt.image.BufferedImage;

@Slf4j
public abstract class SkinRenderer {
    /**
     * Renders the skin part for the player's skin.
     *
     * @param skin the player's skin
     * @param part the skin part to render
     * @param renderOverlays should the overlays be rendered
     * @param size the output size (height; width derived per part)
     * @return the rendered skin part
     */
    public abstract BufferedImage render(Skin skin, SkinPart part, boolean renderOverlays, int size);

}
