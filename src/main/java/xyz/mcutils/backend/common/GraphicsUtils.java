package xyz.mcutils.backend.common;

import xyz.mcutils.backend.common.font.BitmapFont;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

public class GraphicsUtils {
    /**
     * Draws a string using the given bitmap font and returns the x position after the last character.
     */
    public static int drawString(Graphics2D g, BitmapFont font, String str, int x, int y) {
        return drawString(g, font, str, x, y, 1);
    }

    /**
     * Draws a string at the given scale (without mutating the font). When scale != 1, applies a transform
     * so the font renders at native 1x and is scaled up. Returns the x position after the last character.
     */
    public static int drawString(Graphics2D g, BitmapFont font, String str, int x, int y, int scale) {
        if (str == null || str.isEmpty()) return x;
        if (scale == 1) {
            font.drawString(g, str, x, y);
            return x + font.stringWidth(str);
        }
        AffineTransform saved = g.getTransform();
        g.translate(x, y);
        g.scale(scale, scale);
        font.drawString(g, str, 0, 0);
        g.setTransform(saved);
        return x + font.stringWidth(str) * scale;
    }

    /**
     * Draws a string with Minecraft-style style options: shadow (dark offset pass), bold (double-draw +1px),
     * italic (shear transform). Returns the x position after the last character (advance = stringWidth + 1 if bold).
     */
    public static int drawStringWithStyle(Graphics2D g, BitmapFont font, String str, int x, int y,
                                         boolean shadow, boolean bold, boolean italic) {
        return drawStringWithStyle(g, font, str, x, y, shadow, bold, italic, 1);
    }

    /**
     * Draws a string with style at the given scale (without mutating the font). When scale != 1, applies
     * a transform so the font renders at native 1x and is scaled up.
     */
    public static int drawStringWithStyle(Graphics2D g, BitmapFont font, String str, int x, int y,
                                         boolean shadow, boolean bold, boolean italic, int scale) {
        if (str == null || str.isEmpty()) return x;
        AffineTransform savedTransform = g.getTransform();
        int drawX = x;
        int drawY = y;
        if (scale != 1) {
            g.translate(x, y);
            g.scale(scale, scale);
            drawX = 0;
            drawY = 0;
        }
        if (italic) {
            g.shear(-0.2, 0);
        }
        Color savedColor = g.getColor();
        if (shadow) {
            g.setColor(new Color(
                (int) (savedColor.getRed() * 0.25f),
                (int) (savedColor.getGreen() * 0.25f),
                (int) (savedColor.getBlue() * 0.25f),
                savedColor.getAlpha()
            ));
            font.drawString(g, str, drawX + 1, drawY + 1, bold);
            if (bold) {
                font.drawString(g, str, drawX + 2, drawY + 1, bold);  // Shadow for bold copy
            }
            g.setColor(savedColor);
        }
        font.drawString(g, str, drawX, drawY, bold);
        if (bold) {
            font.drawString(g, str, drawX + 1, drawY, bold);
        }
        g.setTransform(savedTransform);
        int advance = font.stringWidth(str, bold);
        return x + advance * scale;
    }

    /**
     * Returns the width of the string when drawn at the given scale.
     */
    public static int stringWidthAtScale(BitmapFont font, String str, int scale) {
        return font.stringWidth(str, false) * scale;
    }

    /**
     * Returns the width of the string when drawn at the given scale with bold styling.
     */
    public static int stringWidthAtScale(BitmapFont font, String str, int scale, boolean bold) {
        return font.stringWidth(str, bold) * scale;
    }

    /**
     * Wraps a Minecraft-formatted string (with § codes) into lines that fit within maxWidthPx when drawn at scale.
     * Returns at most maxLines lines. Format codes are preserved and carried to the start of each new line.
     */
    public static List<String> wrapFormattedToWidth(BitmapFont font, String line, int maxWidthPx, int scale, int maxLines) {
        List<String> result = new ArrayList<>();
        if (line == null || line.isEmpty()) return result;

        StringBuilder formatPrefix = new StringBuilder();
        StringBuilder currentLine = new StringBuilder();
        int i = 0;

        while (i < line.length() && result.size() < maxLines) {
            if (line.charAt(i) == '§' && i + 1 < line.length()) {
                char code = Character.toLowerCase(line.charAt(i + 1));
                if (code == 'x' && i + 14 <= line.length()) {
                    formatPrefix.setLength(0);
                    formatPrefix.append(line, i, i + 14);
                    i += 14;
                    continue;
                }
                if (code == 'r') {
                    formatPrefix.setLength(0);
                    i += 2;
                    continue;
                }
                if ("0123456789abcdefklmnor".indexOf(code) >= 0) {
                    if (code != 'x') formatPrefix.append(line, i, i + 2);
                    i += 2;
                    continue;
                }
            }

            char c = line.charAt(i++);
            String testVisible = ColorUtils.stripColor(formatPrefix.toString() + currentLine.toString() + c);
            int w = font.stringWidth(testVisible) * scale;

            if (w > maxWidthPx && !currentLine.isEmpty()) {
                result.add(formatPrefix.toString() + currentLine.toString());
                currentLine.setLength(0);
                currentLine.append(c);
            } else {
                currentLine.append(c);
            }
        }

        if (!currentLine.isEmpty() && result.size() < maxLines) {
            result.add(formatPrefix.toString() + currentLine.toString());
        }
        return result;
    }
}
