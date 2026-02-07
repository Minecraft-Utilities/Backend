package xyz.mcutils.backend.model.token.mojang;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import xyz.mcutils.backend.Constants;
import xyz.mcutils.backend.common.Tuple;

import java.util.Base64;

@Getter @NoArgsConstructor @AllArgsConstructor
public class MojangProfileToken {
    /**
     * The UUID of the player.
     */
    private String id;

    /**
     * The name of the player.
     */
    private String name;

    /**
     * Is this profile legacy?
     * <p>
     * A "Legacy" profile is a profile that
     * has not yet migrated to a Mojang account.
     * </p>
     * <p>
     * May be null when omitted by the API; treated as false.
     * </p>
     */
    private Boolean legacy;

    /**
     * Whether this profile is legacy (unmigrated). Null from API is treated as false.
     */
    public boolean isLegacy() {
        return Boolean.TRUE.equals(legacy);
    }

    /**
     * The properties of the player.
     */
    private ProfileProperty[] properties = new ProfileProperty[0];
    
    /**
     * Get the skin and cape of the player.
     */
    public Tuple<SkinTextureToken, CapeTextureToken> getSkinAndCape() {
        ProfileProperty textureProperty = getProfileProperty("textures");
        if (textureProperty == null) {
            return null;
        }
        DecodedTexturesPropertyToken decoded = textureProperty.getDecodedTextures();
        if (decoded == null || decoded.getTextures() == null) {
            return null;
        }
        TexturesToken textures = decoded.getTextures();
        return new Tuple<>(textures.getSkin(), textures.getCape());
    }
    
    /**
     * Get a profile property for the player
     *
     * @return the profile property
     */
    public ProfileProperty getProfileProperty(String name) {
        for (ProfileProperty property : properties) {
            if (property.getName().equals(name)) {
                return property;
            }
        }
        return null;
    }

    @Getter @NoArgsConstructor
    public static class ProfileProperty {
        /**
         * The name of the property.
         */
        private String name;

        /**
         * The base64 value of the property.
         */
        private String value;

        /**
         * The signature of the property.
         */
        private String signature;

        /**
         * Decodes the value for this property.
         *
         * @return the decoded value
         */
        @JsonIgnore
        public JsonObject getDecodedValue() {
            return Constants.GSON.fromJson(new String(Base64.getDecoder().decode(this.value)), JsonObject.class);
        }

        /**
         * Decodes the value as the textures property payload (for name "textures").
         *
         * @return the decoded textures payload, or null if decoding fails
         */
        @JsonIgnore
        public DecodedTexturesPropertyToken getDecodedTextures() {
            return Constants.GSON.fromJson(new String(Base64.getDecoder().decode(this.value)), DecodedTexturesPropertyToken.class);
        }

        /**
         * Check if the property is signed.
         *
         * @return true if the property is signed, false otherwise
         */
        public boolean isSigned() {
            return signature != null;
        }
    }
}
