package xyz.mcutils.backend.common.renderer.impl.skin.fullbody;

import java.awt.image.BufferedImage;

import lombok.SneakyThrows;
import xyz.mcutils.backend.common.renderer.SkinRenderer;
import xyz.mcutils.backend.common.renderer.impl.skin.fullbody.IsometricFullBodyRendererBase.Side;
import xyz.mcutils.backend.model.skin.ISkinPart;
import xyz.mcutils.backend.model.skin.Skin;

public class IsometricFullBodyRendererFront extends SkinRenderer<ISkinPart.Custom> {
    public static final IsometricFullBodyRendererFront INSTANCE = new IsometricFullBodyRendererFront();

    @Override
    @SneakyThrows
    public BufferedImage render(Skin skin, ISkinPart.Custom part, boolean renderOverlays, int size) {
        return IsometricFullBodyRendererBase.INSTANCE.render(skin, part, Side.FRONT, renderOverlays, size);
    }
}
