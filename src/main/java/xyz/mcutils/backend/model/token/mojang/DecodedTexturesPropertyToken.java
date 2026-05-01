package xyz.mcutils.backend.model.token.mojang;

/**
 * Token for the decoded value of the "textures" profile property from Mojang.
 * The Base64-decoded payload contains timestamp, profileId, profileName and textures.
 *
 * @param textures The textures object (SKIN and CAPE).
 */
public record DecodedTexturesPropertyToken(TexturesToken textures) {}
