package xyz.mcutils.backend.common.renderer;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.awt.image.BufferedImage;
import java.util.Set;

/**
 * Contract for types that can be rendered by part (e.g. skin parts, cape parts).
 *
 * @param <T> the type that is rendered (e.g. Skin, Cape)
 * @param <P> the part enum type
 */
public interface PartRenderable<T, P extends Enum<P>> {

    @JsonIgnore
    Set<P> getSupportedParts();

    default boolean supportsPart(P part) {
        return part != null && getSupportedParts().contains(part);
    }

    BufferedImage render(P part, int size, RenderOptions options);
}
