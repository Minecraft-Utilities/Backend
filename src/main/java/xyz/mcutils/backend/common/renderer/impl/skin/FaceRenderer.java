package xyz.mcutils.backend.common.renderer.impl.skin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import xyz.mcutils.backend.common.renderer.SkinRenderer;
import xyz.mcutils.backend.model.skin.Skin;
import xyz.mcutils.backend.model.skin.SkinPart;

import java.awt.image.BufferedImage;

@AllArgsConstructor @Getter
public class FaceRenderer extends SkinRenderer {
    public static final FaceRenderer INSTANCE = new FaceRenderer();

    @Override
    public BufferedImage render(Skin skin, SkinPart part, boolean renderOverlays, int size) {
        return HeadRenderer.INSTANCE.render(skin, part, renderOverlays, size, 0, 0);
    }
}
