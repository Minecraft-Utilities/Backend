package xyz.mcutils.backend.common;

import java.awt.*;

public class GraphicsUtils {
    private static final Font FALLBACK_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, Fonts.MINECRAFT.getSize());

    /**
     * Draws a string using the given font for supported characters and a fallback font for
     */
    public static int drawString(Graphics2D g, Font font, String str, int x, int y) {
        if (str == null || str.isEmpty()) return x;
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < str.length(); ) {
            int cp = str.codePointAt(i);
            int len = Character.charCount(cp);
            Font need = font.canDisplay(cp) ? font : FALLBACK_FONT;
            if (run.length() > 0 && need != font) {
                g.setFont(font);
                String s = run.toString();
                g.drawString(s, x, y);
                x += g.getFontMetrics().stringWidth(s);
                run.setLength(0);
            }
            font = need;
            run.appendCodePoint(cp);
            i += len;
        }
        if (run.length() > 0) {
            g.setFont(font);
            String s = run.toString();
            g.drawString(s, x, y);
            x += g.getFontMetrics().stringWidth(s);
        }
        g.setFont(font);
        return x;
    }
}
