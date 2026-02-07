package xyz.mcutils.backend.model.token.mojang;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Token for the SKIN entry in Mojang textures payload.
 *
 * @see #url
 * @see #metadata
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SkinTextureToken {

    /**
     * Full URL of the skin texture, e.g. http://textures.minecraft.net/texture/&lt;id&gt;
     */
    private String url;

    /**
     * Optional metadata (e.g. model: "slim").
     */
    private Metadata metadata;

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

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metadata {
        /**
         * Skin model: "slim" or "default".
         */
        private String model;
    }
}
