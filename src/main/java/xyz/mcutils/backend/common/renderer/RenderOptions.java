package xyz.mcutils.backend.common.renderer;

/**
 * Optional parameters for rendering. Used by renderers that support flags such as overlay layers.
 */
public record RenderOptions(boolean renderOverlays) {

    public static final RenderOptions EMPTY = new RenderOptions(false);

    public static RenderOptions of(boolean renderOverlays) {
        return renderOverlays ? new RenderOptions(true) : EMPTY;
    }
}
