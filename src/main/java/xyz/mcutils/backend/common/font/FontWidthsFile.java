package xyz.mcutils.backend.common.font;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * Minecraft font widths (mc-fonts format). Optional resource for exact Minecraft advance values.
 * See <a href="https://github.com/Owen1212055/mc-fonts">mc-fonts</a>.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FontWidthsFile {

    @JsonProperty("missing_char")
    private CharWidthEntry missingChar;

    @JsonProperty("chars")
    private Map<String, CharWidthEntry> chars;

    /**
     * Per-character width entry (Minecraft uses width for advance; bold_offset for bold advance).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CharWidthEntry(
            int width,
            @JsonProperty("bold_offset") double boldOffset,
            @JsonProperty("shadow_offset") double shadowOffset
    ) {
        @JsonCreator
        public CharWidthEntry(
                @JsonProperty("width") int width,
                @JsonProperty("bold_offset") Double boldOffset,
                @JsonProperty("shadow_offset") Double shadowOffset
        ) {
            this(width, boldOffset != null ? boldOffset : 1.0, shadowOffset != null ? shadowOffset : 1.0);
        }
    }

    public int getAdvance(int codepoint) {
        CharWidthEntry e = getCharWidthEntry(codepoint);
        return e != null ? e.width() : -1;
    }

    public CharWidthEntry getCharWidthEntry(int codepoint) {
        if (chars == null) {
            return null;
        }
        String key = Character.toString(codepoint);
        CharWidthEntry e = chars.get(key);
        if (e != null) {
            return e;
        }
        if (Character.isSupplementaryCodePoint(codepoint)) {
            key = new String(Character.toChars(codepoint));
            return chars.get(key);
        }
        return null;
    }

    public int getMissingCharWidth() {
        return missingChar != null ? missingChar.width() : 6;
    }
}
