package xyz.mcutils.backend.common.renderer.impl.skin.isometric;

import lombok.SneakyThrows;
import xyz.mcutils.backend.common.renderer.RenderOptions;
import xyz.mcutils.backend.common.renderer.SkinRenderer;
import xyz.mcutils.backend.common.renderer.impl.skin.isometric.FullBodyIsoRendererBase.Side;
import xyz.mcutils.backend.model.domain.skin.Skin;

import java.awt.image.BufferedImage;

public class FullBodyIsoRendererBack extends SkinRenderer {
    public static final FullBodyIsoRendererBack INSTANCE = new FullBodyIsoRendererBack();

    @Override
    @SneakyThrows
    public BufferedImage render(Skin skin, int size, RenderOptions options) {
        return FullBodyIsoRendererBase.INSTANCE.render(skin, Side.BACK, options.renderOverlays(), size);
    }
}
