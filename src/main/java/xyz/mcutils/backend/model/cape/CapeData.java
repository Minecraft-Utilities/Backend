package xyz.mcutils.backend.model.cape;

import java.util.Map;

import xyz.mcutils.backend.config.AppConfig;

/*
 * The information about a cape.
 *   
 * @param name the name of the cape
 * @param textureId the texture id of the cape
 */
public record CapeData(String name, String textureId) {
    /**
     * Gets the part URLs for the cape.
     * 
     * @return the part URLs for the cape
     */
    public Map<String, String> getParts() {
        return Cape.buildParts(this.textureId);
    }

    /**
     * Gets the texture URL for the cape.
     * 
     * @return the texture URL for the cape
     */
    public String getTextureUrl() {
        return AppConfig.INSTANCE.getWebPublicUrl() + "/capes/%s/texture.png".formatted(this.textureId);
    }
}
