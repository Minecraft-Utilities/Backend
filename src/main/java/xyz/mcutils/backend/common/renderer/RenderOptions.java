package xyz.mcutils.backend.common.renderer;

import org.jetbrains.annotations.Nullable;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;

/**
 * Render options for rendering.
 *
 * @param renderOverlays whether to render the overlays
 * @param cape           optional cape to render alongside the skin (only used by full-body isometric renderers)
 */
public record RenderOptions(boolean renderOverlays, @Nullable VanillaCape cape) {
    public static final RenderOptions DEFAULT = new RenderOptions(false);

    public RenderOptions(boolean renderOverlays) {
        this(renderOverlays, null);
    }
}
