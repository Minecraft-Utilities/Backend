package xyz.mcutils.backend.common.renderer.impl.skin;

import xyz.mcutils.backend.common.renderer.SkinRenderer;
import xyz.mcutils.backend.common.renderer.Software3DRenderer;
import xyz.mcutils.backend.model.skin.ISkinPart;
import xyz.mcutils.backend.model.skin.Skin;

import java.awt.image.BufferedImage;

/**
 * Renders a full Minecraft player body using software 3D rendering,
 * matching nmsr-rs FullBodyIso (orthographic, yaw 45°, pitch 35.264°).
 * Output is rectangle only: width = size * (512/869), height = size.
 */
public class IsometricFullBodyRenderer extends SkinRenderer<ISkinPart.Custom> {
    public static final IsometricFullBodyRenderer INSTANCE = new IsometricFullBodyRenderer();

    @Override
    public BufferedImage render(Skin skin, ISkinPart.Custom part, boolean renderOverlays, int size) {
        return Software3DRenderer.render(skin, renderOverlays, size);
    }
}
