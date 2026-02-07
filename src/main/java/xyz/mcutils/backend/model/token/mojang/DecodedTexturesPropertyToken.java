package xyz.mcutils.backend.model.token.mojang;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Token for the decoded value of the "textures" profile property from Mojang.
 * The Base64-decoded payload contains timestamp, profileId, profileName and textures.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DecodedTexturesPropertyToken {

    /**
     * The textures object (SKIN and CAPE).
     */
    private TexturesToken textures;
}
