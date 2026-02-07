package xyz.mcutils.backend.model.token.mojang;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Token for the CAPE entry in Mojang textures payload.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CapeTextureToken {

    /**
     * Full URL of the cape texture, e.g. http://textures.minecraft.net/texture/&lt;id&gt;
     */
    private String url;

    /**
     * Extracts the texture id from the URL (last path segment).
     */
    public String getTextureId() {
        if (url == null) {
            return null;
        }
        String[] parts = url.split("/");
        return parts[parts.length - 1];
    }
}
