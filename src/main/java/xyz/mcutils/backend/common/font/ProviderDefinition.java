package xyz.mcutils.backend.common.font;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Single provider in a Minecraft-style font definition (bitmap or ttf).
 * Only bitmap is used in this implementation.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProviderDefinition {
    private String type;
    private String file;
    private Integer ascent;
    private Integer height;
    private List<String> chars;
}
