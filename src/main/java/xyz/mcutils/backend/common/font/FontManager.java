package xyz.mcutils.backend.common.font;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import xyz.mcutils.backend.Main;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

/**
 * Loads Minecraft-style font definitions from JSON and exposes a texture-based BitmapFont.
 */
@Slf4j
public class FontManager {

    private static final String DEFAULT_FONT_PATH = "/font/default.json";
    private static final String DEFAULT_WIDTHS_PATH = "/font/default_widths.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static FontManager instance;
    private BitmapFont defaultFont;

    public static synchronized FontManager getInstance() {
        if (instance == null) {
            instance = new FontManager();
        }
        return instance;
    }

    /**
     * Load font/default.json and build the default BitmapFont. Idempotent after first successful load.
     * If /font/default_widths.json exists (mc-fonts format), uses exact Minecraft advance values.
     */
    public synchronized void load() {
        if (defaultFont != null) {
            return;
        }
        FontWidthsFile widthsFile = loadWidths();
        try (InputStream in = Main.class.getResourceAsStream(DEFAULT_FONT_PATH)) {
            if (in == null) {
                log.error("Font definition not found: {}", DEFAULT_FONT_PATH);
                return;
            }
            FontDefinitionFile def = MAPPER.readValue(in, FontDefinitionFile.class);
            List<ProviderDefinition> providers = def.getProviders();
            if (providers == null || providers.isEmpty()) {
                log.warn("No providers in font definition");
                return;
            }
            int ascent = 7;
            int height = 8;
            int defaultGlyphWidth = 8;
            BitmapFont font = null;
            for (ProviderDefinition provider : providers) {
                if (!"bitmap".equalsIgnoreCase(provider.getType())) {
                    continue;
                }
                String path = FontResourceResolver.resolve(provider.getFile());
                if (path == null) {
                    log.warn("Could not resolve font file: {}", provider.getFile());
                    continue;
                }
                try (InputStream imgIn = Main.class.getResourceAsStream(path)) {
                    if (imgIn == null) {
                        log.warn("Font texture not found: {}", path);
                        continue;
                    }
                    BufferedImage texture = ImageIO.read(imgIn);
                    if (texture == null) {
                        log.warn("Failed to read font texture: {}", path);
                        continue;
                    }
                    List<String> chars = provider.getChars();
                    if (chars == null || chars.isEmpty()) {
                        continue;
                    }
                    int rows = chars.size();
                    int cols = chars.get(0).length();
                    int cellW = texture.getWidth() / cols;
                    int cellH = texture.getHeight() / rows;
                    int pAscent = provider.getAscent() != null ? provider.getAscent() : 7;
                    int pHeight = provider.getHeight() != null ? provider.getHeight() : 8;
                    if (font == null) {
                        ascent = pAscent;
                        height = pHeight;
                        defaultGlyphWidth = widthsFile != null ? widthsFile.getMissingCharWidth() : cellW;
                        font = new BitmapFont(ascent, height, defaultGlyphWidth);
                    }
                    for (int row = 0; row < rows; row++) {
                        String line = chars.get(row);
                        for (int col = 0, i = 0; col < cols && i < line.length(); col++) {
                            int cp = line.codePointAt(i);
                            if (cp != 0) {
                                int sx = col * cellW;
                                int sy = row * cellH;
                                int advance = getAdvance(widthsFile, texture, sx, sy, cellW, cellH, cp);
                                Glyph glyph = new Glyph(texture, sx, sy, cellW, cellH, advance);
                                font.putGlyph(cp, glyph);
                            }
                            i += Character.charCount(cp);
                        }
                    }
                }
            }
            if (font != null) {
                defaultFont = font;
                log.info("Loaded bitmap font from {}" + (widthsFile != null ? " (Minecraft widths)" : ""), DEFAULT_FONT_PATH);
            }
        } catch (Exception e) {
            log.error("Failed to load font definition: {}", DEFAULT_FONT_PATH, e);
        }
    }

    private static FontWidthsFile loadWidths() {
        try (InputStream in = Main.class.getResourceAsStream(DEFAULT_WIDTHS_PATH)) {
            if (in != null) {
                return MAPPER.readValue(in, FontWidthsFile.class);
            }
        } catch (Exception e) {
            // optional
        }
        return null;
    }

    private static int getAdvance(FontWidthsFile widths, BufferedImage texture, int srcX, int srcY, int cellW, int cellH, int codepoint) {
        if (widths != null) {
            int w = widths.getAdvance(codepoint);
            if (w >= 0) return w;
        }
        return Glyph.measureAdvance(texture, srcX, srcY, cellW, cellH);
    }

    /**
     * Returns the default BitmapFont. Loads font definition on first call if not yet loaded.
     */
    public BitmapFont getDefaultFont() {
        if (defaultFont == null) {
            load();
        }
        return Objects.requireNonNull(defaultFont, "Font failed to load");
    }
}
