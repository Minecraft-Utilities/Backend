package xyz.mcutils.backend.common.renderer.impl.skin.fullbody;

import lombok.SneakyThrows;
import xyz.mcutils.backend.common.renderer.RenderOptions;
import xyz.mcutils.backend.common.renderer.SkinRenderer;
import xyz.mcutils.backend.common.renderer.impl.skin.fullbody.FullBodyRendererBase.Side;
import xyz.mcutils.backend.model.skin.Skin;

import java.awt.image.BufferedImage;

public class FullBodyRendererBack extends SkinRenderer {
    public static final FullBodyRendererBack INSTANCE = new FullBodyRendererBack();

    @Override
    @SneakyThrows
    public BufferedImage render(Skin skin, int size, RenderOptions options) {
        return FullBodyRendererBase.INSTANCE.render(skin, Side.BACK, options.renderOverlays(), size);
    }
}
