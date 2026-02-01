package xyz.mcutils.backend.common.renderer.impl.skin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import xyz.mcutils.backend.common.renderer.SkinRenderer;
import xyz.mcutils.backend.model.skin.ISkinPart;
import xyz.mcutils.backend.model.skin.Skin;

import java.awt.image.BufferedImage;

@AllArgsConstructor @Getter
public class FaceRenderer extends SkinRenderer<ISkinPart.Custom> {
    public static final FaceRenderer INSTANCE = new FaceRenderer();

    @Override
    public BufferedImage render(Skin skin, ISkinPart.Custom part, boolean renderOverlays, int size) {
        return IsometricHeadRenderer.INSTANCE.render(skin, part, renderOverlays, size, 0, 0);
    }
}
