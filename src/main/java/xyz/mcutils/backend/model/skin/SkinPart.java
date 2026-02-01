package xyz.mcutils.backend.model.skin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import xyz.mcutils.backend.common.renderer.SkinRenderer;
import xyz.mcutils.backend.common.renderer.impl.skin.BodyRenderer;
import xyz.mcutils.backend.common.renderer.impl.skin.FaceRenderer;
import xyz.mcutils.backend.common.renderer.impl.skin.HeadRenderer;
import xyz.mcutils.backend.common.renderer.impl.skin.fullbody.FullBodyRendererBack;
import xyz.mcutils.backend.common.renderer.impl.skin.fullbody.FullBodyRendererFront;

import java.awt.image.BufferedImage;

@AllArgsConstructor
@Getter
public enum SkinPart {
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
        return renderer.render(skin, renderOverlays, size);
    }

    /**
     * Gets a skin part by name.
     *
     * @param name the name of the skin part
     * @return the skin part
     */
    public static SkinPart getByName(String name) {
        for (SkinPart part : SkinPart.values()) {
            if (part.name().equalsIgnoreCase(name)) {
                return part;
            }
        }
        return null;
    }
}
