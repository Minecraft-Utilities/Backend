package xyz.mcutils.backend.common.renderer.impl.skin.fullbody;

import lombok.SneakyThrows;
import xyz.mcutils.backend.common.renderer.SkinRenderer;
import xyz.mcutils.backend.common.renderer.impl.skin.fullbody.FullBodyRendererBase.Side;
import xyz.mcutils.backend.model.skin.ISkinPart;
import xyz.mcutils.backend.model.skin.Skin;

import java.awt.image.BufferedImage;

public class FullBodyRendererFront extends SkinRenderer<ISkinPart.Custom> {
    public static final FullBodyRendererFront INSTANCE = new FullBodyRendererFront();

    @Override
    @SneakyThrows
    public BufferedImage render(Skin skin, ISkinPart.Custom part, boolean renderOverlays, int size) {
        return FullBodyRendererBase.INSTANCE.render(skin, part, Side.FRONT, renderOverlays, size);
    }
}
