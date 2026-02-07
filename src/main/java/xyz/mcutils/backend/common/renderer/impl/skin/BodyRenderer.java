package xyz.mcutils.backend.common.renderer.impl.skin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import xyz.mcutils.backend.common.renderer.RenderOptions;
import xyz.mcutils.backend.common.renderer.SkinRenderer;
import xyz.mcutils.backend.common.renderer.impl.skin.fullbody.FullBodyRendererBase;
import xyz.mcutils.backend.common.renderer.impl.skin.fullbody.FullBodyRendererBase.Side;
import xyz.mcutils.backend.model.domain.skin.Skin;

import java.awt.image.BufferedImage;

@AllArgsConstructor @Getter
public class BodyRenderer extends SkinRenderer {
    public static final BodyRenderer INSTANCE = new BodyRenderer();

    @Override
    public BufferedImage render(Skin skin, int size, RenderOptions options) {
        return FullBodyRendererBase.INSTANCE.render(skin, Side.FRONT, options.renderOverlays(), size, 0, 14.2);
    }
}
