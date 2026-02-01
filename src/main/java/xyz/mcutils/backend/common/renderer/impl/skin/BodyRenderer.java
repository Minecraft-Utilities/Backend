package xyz.mcutils.backend.common.renderer.impl.skin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import xyz.mcutils.backend.common.renderer.SkinRenderer;
import xyz.mcutils.backend.common.renderer.impl.skin.fullbody.FullBodyRendererBase;
import xyz.mcutils.backend.common.renderer.impl.skin.fullbody.FullBodyRendererBase.Side;
import xyz.mcutils.backend.model.skin.Skin;
import xyz.mcutils.backend.model.skin.SkinPart;

import java.awt.image.BufferedImage;

@AllArgsConstructor @Getter
public class BodyRenderer extends SkinRenderer {
    public static final BodyRenderer INSTANCE = new BodyRenderer();

    @Override
    public BufferedImage render(Skin skin, SkinPart part, boolean renderOverlays, int size) {
        return FullBodyRendererBase.INSTANCE.render(skin, part, Side.FRONT, renderOverlays, size, 0, 14.5);
    }
}
