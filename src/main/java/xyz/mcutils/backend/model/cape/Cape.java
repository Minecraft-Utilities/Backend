package xyz.mcutils.backend.model.cape;

import lombok.*;
import xyz.mcutils.backend.common.renderer.PartRenderable;
import xyz.mcutils.backend.model.Texture;

import java.util.Map;

@Getter
@EqualsAndHashCode(callSuper = false)
@ToString
@NoArgsConstructor
public abstract class Cape<P extends Enum<P>> extends Texture implements PartRenderable<Cape<P>, P> {
    /**
     * The parts of the cape (render type name -> URL to rendered image).
     */
    @Setter
    private Map<String, String> parts;

    public Cape(String textureId, String rawTextureUrl, String textureUrl, Map<String, String> parts) {
        super(textureId, rawTextureUrl, textureUrl);
        this.parts = parts;
    }

    public abstract P fromPartName(String name);
}
