package xyz.mcutils.backend.common;

/**
 * Shared utilities for renderers (e.g. skin, cape).
 */
public final class RendererUtils {

    private RendererUtils() {
    }

    /**
     * Scales a logical position (x or y) to output pixels. Use for destination
     * coordinates so that 0 stays 0 and no transparent gap is left at the edge.
     *
     * @param logical logical units (e.g. 0 for top/left edge)
     * @param scale   scale factor (e.g. output height / 32)
     * @return scaled pixel value
     */
    public static int scaleLogicalCoord(int logical, double scale) {
        return (int) (logical * scale);
    }

    /**
     * Scales a logical size (width or height) to output pixels. Ensures at least
     * 1 pixel so zero-sized regions are never drawn.
     *
     * @param logical logical units (e.g. 8 for 8px in a 16×32 layout)
     * @param scale   scale factor (e.g. output height / 32)
     * @return scaled pixel value, at least 1
     */
    public static int scaleLogical(int logical, double scale) {
        return Math.max(1, (int) (logical * scale));
    }
}
