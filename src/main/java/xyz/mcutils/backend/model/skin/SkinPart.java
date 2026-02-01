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

    public BufferedImage render(Skin skin, boolean renderOverlays, int size) {
        return renderer.render(skin, this, renderOverlays, size);
    }

    public boolean isFullBody() {
        return this == FULLBODY_FRONT || this == FULLBODY_BACK;
    }
}
