package xyz.mcutils.backend.common.renderer;

/**
 * Render options for rendering.
 *
 * @param renderOverlays whether to render the overlays
 */
public record RenderOptions(boolean renderOverlays) {
    public static final RenderOptions DEFAULT = new RenderOptions(false);
}
