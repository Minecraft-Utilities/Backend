/**
 * Encode the skin textures into a JSON object
 *
 * @param textureId the texture id of the skin
 * @returns the JSON object
 */
export function encodeSkinTextures(textureId: string) {
  return { textures: { SKIN: { url: `http://textures.minecraft.net/texture/${textureId}` } } };
}
