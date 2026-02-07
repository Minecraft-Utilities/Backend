package xyz.mcutils.backend.common.renderer.impl.skin.fullbody;

import lombok.SneakyThrows;
import xyz.mcutils.backend.common.renderer.RenderOptions;
import xyz.mcutils.backend.common.renderer.SkinRenderer;
import xyz.mcutils.backend.common.renderer.impl.skin.fullbody.FullBodyRendererBase.Side;
import xyz.mcutils.backend.model.skin.Skin;

import java.awt.image.BufferedImage;

public class FullBodyRendererFront extends SkinRenderer {
    public static final FullBodyRendererFront INSTANCE = new FullBodyRendererFront();

    @Override
    @SneakyThrows
    public BufferedImage render(Skin skin, int size, RenderOptions options) {
        return FullBodyRendererBase.INSTANCE.render(skin, Side.FRONT, options.renderOverlays(), size);
    }
}
