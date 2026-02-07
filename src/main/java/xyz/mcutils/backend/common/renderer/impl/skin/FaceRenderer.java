package xyz.mcutils.backend.common.renderer.impl.skin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import xyz.mcutils.backend.common.renderer.RenderOptions;
import xyz.mcutils.backend.common.renderer.SkinRenderer;
import xyz.mcutils.backend.model.skin.Skin;

import java.awt.image.BufferedImage;

@AllArgsConstructor @Getter
public class FaceRenderer extends SkinRenderer {
    public static final FaceRenderer INSTANCE = new FaceRenderer();

    @Override
    public BufferedImage render(Skin skin, int size, RenderOptions options) {
        return HeadRenderer.INSTANCE.render(skin, size, options, 0, 0);
    }
}
