package xyz.mcutils.backend.service;

import org.springframework.stereotype.Service;
import xyz.mcutils.backend.model.skin.Skin;
import xyz.mcutils.backend.model.skin.SkinPart;

import java.awt.image.BufferedImage;

/**
 * Central service for all skin rendering.
 */
@Service
public class RendererService {

    /**
     * Looks up a skin part by name (case-insensitive).
     *
     * @param name the part name
     * @return the part, or null if not found
     */
    public SkinPart getSkinPartByName(String name) {
        if (name == null) return null;
        name = name.toUpperCase();
        for (SkinPart part : SkinPart.values()) {
            if (part.name().equals(name)) {
                return part;
            }
        }
        return null;
    }

    /**
     * Renders a skin part.
     *
     * @param skin           the skin
     * @param part           the part to render
     * @param renderOverlays whether to include overlays
     * @param size           output size (height)
     * @return the rendered image
     */
    public BufferedImage renderSkinPart(Skin skin, SkinPart part, boolean renderOverlays, int size) {
        return part.render(skin, renderOverlays, size);
    }

}
