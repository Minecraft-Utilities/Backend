package xyz.mcutils.backend.common;

import lombok.extern.slf4j.Slf4j;
import xyz.mcutils.backend.Main;

import java.awt.*;
import java.io.IOException;
import java.util.Objects;

@Slf4j
public class Fonts {
    public static final Font MINECRAFT;
    public static final Font MINECRAFT_BOLD;
    public static final Font MINECRAFT_ITALIC;

    static {
        try {
            // Create the font at a pixel-perfect size (typically 8f or 16f)
            // The key change is using a size that matches the original pixel size
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            Font baseFont = Font.createFont(Font.TRUETYPE_FONT, Objects.requireNonNull(Main.class.getResourceAsStream("/fonts/minecraft-font.ttf"), "Minecraft font not found"));
            ge.registerFont(baseFont);

            // Use a specific size that matches the pixel grid (try 8f, 16f, or 12f)
            MINECRAFT = baseFont.deriveFont(Font.PLAIN, 16f);
            MINECRAFT_BOLD = baseFont.deriveFont(Font.BOLD, 16f);
            MINECRAFT_ITALIC = baseFont.deriveFont(Font.ITALIC, 16f);
        } catch (FontFormatException | IOException e) {
            log.error("Failed to load Minecraft font", e);
            throw new RuntimeException("Minecraft font was not loaded", e);
        }
    }
}
