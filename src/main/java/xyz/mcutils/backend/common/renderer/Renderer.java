package xyz.mcutils.backend.common.renderer;

import java.awt.image.BufferedImage;

public abstract class Renderer<T> {

    /**
     * Renders the object to the specified size with the given options.
     *
     * @param input   The object to render.
     * @param size    The size to render the object to.
     * @param options Optional rendering flags (e.g. overlay layers); may be ignored by the renderer.
     * @return The rendered image.
     */
    public abstract BufferedImage render(T input, int size, RenderOptions options);

    /**
     * Renders the object to the specified size with default options.
     *
     * @param input The object to render.
     * @param size  The size to render the object to.
     * @return The rendered image.
     */
    public BufferedImage render(T input, int size) {
        return render(input, size, RenderOptions.EMPTY);
    }
}
