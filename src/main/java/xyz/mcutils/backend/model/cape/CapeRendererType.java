package xyz.mcutils.backend.model.cape;

import lombok.AllArgsConstructor;
import lombok.Getter;
import xyz.mcutils.backend.common.renderer.Renderer;
import xyz.mcutils.backend.common.renderer.impl.cape.CapeRenderer;

import java.awt.image.BufferedImage;

@AllArgsConstructor
@Getter
public enum CapeRendererType {
    FRONT(CapeRenderer.INSTANCE);

    private final Renderer<Cape> renderer;

    /**
     * Renders the cape part.
     *
     * @param cape the cape
     * @param size the size of the cape part (height)
     * @return the rendered cape part
     */
    public BufferedImage render(Cape cape, int size) {
        return renderer.render(cape, size);
    }

    /**
     * Gets a cape part by name.
     *
     * @param name the name of the cape part
     * @return the cape part, or null if not found
     */
    public static CapeRendererType getByName(String name) {
        for (CapeRendererType part : CapeRendererType.values()) {
            if (part.name().equalsIgnoreCase(name)) {
                return part;
            }
        }
        return null;
    }
}
