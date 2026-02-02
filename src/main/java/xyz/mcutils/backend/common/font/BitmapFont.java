package xyz.mcutils.backend.common.font;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Texture-based font: draws and measures text by blitting glyph regions from loaded textures.
 */
public class BitmapFont {

    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private final int ascent;
    private final int height;
    private final int defaultGlyphWidth;
    private int scale = 1;

    public BitmapFont(int ascent, int height, int defaultGlyphWidth) {
        this.ascent = ascent;
        this.height = height;
        this.defaultGlyphWidth = defaultGlyphWidth;
    }

    void putGlyph(int codepoint, Glyph glyph) {
        glyphs.put(codepoint, glyph);
    }

    public int getScale() {
        return scale;
    }

    public void setScale(int scale) {
        this.scale = scale;
    }

    public int ascent() {
        return ascent * scale;
    }

    public int height() {
        return height * scale;
    }

    /**
     * Total width of the string in pixels (scaled). Uses per-glyph advance like Minecraft, not fixed cell width.
     */
    public int stringWidth(String str) {
        if (str == null || str.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < str.length(); ) {
            int cp = str.codePointAt(i);
            Glyph g = glyphs.get(cp);
            int adv = g != null ? g.advance() : defaultGlyphWidth;
            total += adv * scale;
            i += Character.charCount(cp);
        }
        return total;
    }

    /**
     * Draw the string with baseline at (x, y). Glyphs are tinted by the current Graphics2D color (Minecraft-style).
     */
    public void drawString(Graphics2D g, String str, int x, int y) {
        if (str == null || str.isEmpty()) {
            return;
        }
        Color color = g.getColor();
        int drawY = y - ascent();
        for (int i = 0; i < str.length(); ) {
            int cp = str.codePointAt(i);
            Glyph glyph = glyphs.get(cp);
            if (glyph != null) {
                int w = glyph.width() * scale;
                int h = glyph.height() * scale;
                drawGlyphTinted(g, glyph, x, drawY, w, h, color);
                x += glyph.advance() * scale;
            } else {
                x += defaultGlyphWidth * scale;
            }
            i += Character.charCount(cp);
        }
    }

    /**
     * Draw a single glyph tinted by the given color (glyph alpha as mask, color for visible pixels).
     */
    private void drawGlyphTinted(Graphics2D g, Glyph glyph, int x, int y, int w, int h, Color color) {
        BufferedImage src = glyph.texture();
        int srcX = glyph.srcX();
        int srcY = glyph.srcY();
        int gw = glyph.width();
        int gh = glyph.height();
        BufferedImage tinted = new BufferedImage(gw, gh, BufferedImage.TYPE_INT_ARGB);
        int cr = color.getRed();
        int cg = color.getGreen();
        int cb = color.getBlue();
        for (int py = 0; py < gh; py++) {
            for (int px = 0; px < gw; px++) {
                int argb = src.getRGB(srcX + px, srcY + py);
                int a = (argb >> 24) & 0xff;
                int sr = (argb >> 16) & 0xff;
                int sg = (argb >> 8) & 0xff;
                int sb = argb & 0xff;
                int tr = (sr * cr) / 255;
                int tg = (sg * cg) / 255;
                int tb = (sb * cb) / 255;
                tinted.setRGB(px, py, (a << 24) | (tr << 16) | (tg << 8) | tb);
            }
        }
        if (scale != 1) {
            Image scaled = tinted.getScaledInstance(w, h, Image.SCALE_FAST);
            g.drawImage(scaled, x, y, w, h, null);
        } else {
            g.drawImage(tinted, x, y, null);
        }
    }

    /**
     * Draw the string and return the x position after the last character (for chaining).
     */
    public int drawStringAndAdvance(Graphics2D g, String str, int x, int y) {
        drawString(g, str, x, y);
        return x + stringWidth(str);
    }
}
