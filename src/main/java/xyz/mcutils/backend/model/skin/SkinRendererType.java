package xyz.mcutils.backend.model.skin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import xyz.mcutils.backend.common.renderer.RenderOptions;
import xyz.mcutils.backend.common.renderer.SkinRenderer;
import xyz.mcutils.backend.common.renderer.impl.skin.BodyRenderer;
import xyz.mcutils.backend.common.renderer.impl.skin.FaceRenderer;
import xyz.mcutils.backend.common.renderer.impl.skin.HeadRenderer;
import xyz.mcutils.backend.common.renderer.impl.skin.fullbody.FullBodyRendererBack;
import xyz.mcutils.backend.common.renderer.impl.skin.fullbody.FullBodyRendererFront;

import java.awt.image.BufferedImage;

@AllArgsConstructor
@Getter
public enum SkinRendererType {
    FACE(FaceRenderer.INSTANCE),
    HEAD(HeadRenderer.INSTANCE),
    FULLBODY_FRONT(FullBodyRendererFront.INSTANCE),
    FULLBODY_BACK(FullBodyRendererBack.INSTANCE),
    BODY(BodyRenderer.INSTANCE);

    private final SkinRenderer renderer;

    /**
     * Renders the skin part.
     *
     * @param skin the skin
     * @param renderOverlays whether to render the overlays
     * @param size the size of the skin part
     * @return the rendered skin part
     */
    public BufferedImage render(Skin skin, boolean renderOverlays, int size) {
        return renderer.render(skin, size, RenderOptions.of(renderOverlays));
    }

    /**
     * Gets a skin part by name.
     *
     * @param name the name of the skin part
     * @return the skin part
     */
    public static SkinRendererType getByName(String name) {
        for (SkinRendererType part : SkinRendererType.values()) {
            if (part.name().equalsIgnoreCase(name)) {
                return part;
            }
        }
        return null;
    }
}
