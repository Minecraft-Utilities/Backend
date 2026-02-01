package xyz.mcutils.backend.common.renderer.impl.skin.fullbody;

import lombok.SneakyThrows;
import xyz.mcutils.backend.common.renderer.SkinRenderer;
import xyz.mcutils.backend.common.renderer.impl.skin.fullbody.FullBodyRendererBase.Side;
import xyz.mcutils.backend.model.skin.Skin;
import xyz.mcutils.backend.model.skin.SkinPart;

import java.awt.image.BufferedImage;

public class FullBodyRendererBack extends SkinRenderer {
    public static final FullBodyRendererBack INSTANCE = new FullBodyRendererBack();

    @Override
    @SneakyThrows
    public BufferedImage render(Skin skin, SkinPart part, boolean renderOverlays, int size) {
        return FullBodyRendererBase.INSTANCE.render(skin, part, Side.BACK, renderOverlays, size);
    }
}
