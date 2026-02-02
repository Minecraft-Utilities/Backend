package xyz.mcutils.backend.model.token.mojang;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import xyz.mcutils.backend.Constants;
import xyz.mcutils.backend.common.Tuple;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.model.player.Cape;
import xyz.mcutils.backend.model.player.Player;
import xyz.mcutils.backend.model.skin.Skin;

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
     *
     * @param player the player to get the skin and cape for
     * @return the skin and cape of the player
     */
    public Tuple<Skin, Cape> getSkinAndCape(Player player) {
        ProfileProperty textureProperty = getProfileProperty("textures");
        if (textureProperty == null) {
            return null;
        }
        JsonObject texturesJson = textureProperty.getDecodedValue().getAsJsonObject("textures"); // Parse the decoded JSON and get the texture object
        return new Tuple<>(Skin.fromJson(texturesJson.getAsJsonObject("SKIN"), player),
                Cape.fromJson(texturesJson.getAsJsonObject("CAPE")));
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
         * Check if the property is signed.
         *
         * @return true if the property is signed, false otherwise
         */
        public boolean isSigned() {
            return signature != null;
        }
    }
}
