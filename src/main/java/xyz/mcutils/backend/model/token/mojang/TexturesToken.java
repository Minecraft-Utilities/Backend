package xyz.mcutils.backend.model.token.mojang;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Token for the "textures" object in the decoded Mojang profile property value.
 * <p>
 * JSON shape: {@code {"SKIN": {...}, "CAPE": {...}}}
 * </p>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TexturesToken {

    @SerializedName("SKIN")
    private SkinTextureToken skin;

    @SerializedName("CAPE")
    private CapeTextureToken cape;
}
