package xyz.mcutils.backend.common;

import xyz.mcutils.backend.common.font.BitmapFont;
import xyz.mcutils.backend.common.font.FontManager;

/**
 * Default texture-based Minecraft-style font (bitmap only, no TTF).
 */
public class Fonts {
    public static final BitmapFont MINECRAFT = FontManager.getInstance().getDefaultFont();
}
