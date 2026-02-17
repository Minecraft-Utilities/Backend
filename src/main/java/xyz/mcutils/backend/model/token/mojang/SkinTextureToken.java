package xyz.mcutils.backend.model.token.mojang;

/**
 * Token for the SKIN entry in Mojang textures payload.
 *
 * @param url      Full URL of the skin texture, e.g. http://textures.minecraft.net/texture/&lt;id&gt;
 * @param metadata Optional metadata (e.g. model: "slim").
 */
public record SkinTextureToken(String url, Metadata metadata) {

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

    /**
     * Skin texture metadata (e.g. model: "slim" or "default").
     *
     * @param model Skin model: "slim" or "default".
     */
    public record Metadata(String model) { }
}
