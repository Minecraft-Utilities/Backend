package xyz.mcutils.backend.model.token.mojang;

import com.google.gson.annotations.SerializedName;

/**
 * Token for the "textures" object in the decoded Mojang profile property value.
 * <p>
 * JSON shape: {@code {"SKIN": {...}, "CAPE": {...}}}
 * </p>
 *
 * @param skin The SKIN entry.
 * @param cape The CAPE entry.
 */
public record TexturesToken(
        @SerializedName("SKIN") SkinTextureToken skin,
        @SerializedName("CAPE") CapeTextureToken cape
) {
}
