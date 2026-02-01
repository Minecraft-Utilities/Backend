package xyz.mcutils.backend.common.renderer.impl.skin.fullbody;

import java.awt.image.BufferedImage;

import lombok.SneakyThrows;
import xyz.mcutils.backend.common.renderer.SkinRenderer;
import xyz.mcutils.backend.common.renderer.impl.skin.fullbody.IsometricFullBodyRendererBase.Side;
import xyz.mcutils.backend.model.skin.ISkinPart;
import xyz.mcutils.backend.model.skin.Skin;

public class IsometricFullBodyRendererBack extends SkinRenderer<ISkinPart.Custom> {
    public static final IsometricFullBodyRendererBack INSTANCE = new IsometricFullBodyRendererBack();

    @Override
    @SneakyThrows
    public BufferedImage render(Skin skin, ISkinPart.Custom part, boolean renderOverlays, int size) {
        return IsometricFullBodyRendererBase.INSTANCE.render(skin, part, Side.BACK, renderOverlays, size);
    }
}
