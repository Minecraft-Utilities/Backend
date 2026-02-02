package xyz.mcutils.backend.common.font;

import java.awt.image.BufferedImage;

/**
 * A single glyph: reference to texture, source rectangle, and horizontal advance (Minecraft-style).
 * Advance is the distance to move after drawing this glyph; it can be less than width for narrow characters.
 */
public record Glyph(BufferedImage texture, int srcX, int srcY, int width, int height, int advance) {

    /**
     * Creates a glyph with advance equal to width (monospace cell). Use when advance is not measured.
     */
    public static Glyph withCellAdvance(BufferedImage texture, int srcX, int srcY, int width, int height) {
        return new Glyph(texture, srcX, srcY, width, height, width);
    }

    /**
     * Measures the horizontal advance like Minecraft's bitmap provider: rightmost column with any
     * non-transparent pixel plus one (no extra spacing; use default_widths.json for exact Minecraft values).
     */
    public static int measureAdvance(BufferedImage texture, int srcX, int srcY, int width, int height) {
        int rightmost = -1;
        for (int col = 0; col < width; col++) {
            for (int row = 0; row < height; row++) {
                int a = (texture.getRGB(srcX + col, srcY + row) >> 24) & 0xff;
                if (a > 0) {
                    rightmost = col;
                    break;
                }
            }
        }
        return rightmost >= 0 ? rightmost + 1 : width;
    }
}
