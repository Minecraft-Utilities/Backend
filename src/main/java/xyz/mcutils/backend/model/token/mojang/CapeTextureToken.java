package xyz.mcutils.backend.model.token.mojang;

/**
 * Token for the CAPE entry in Mojang textures payload.
 *
 * @param url Full URL of the cape texture, e.g. http://textures.minecraft.net/texture/&lt;id&gt;
 */
public record CapeTextureToken(String url) {

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
