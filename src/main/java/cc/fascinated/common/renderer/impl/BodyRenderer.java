package cc.fascinated.common.renderer.impl;

import cc.fascinated.common.ImageUtils;
import cc.fascinated.common.renderer.SkinRenderer;
import cc.fascinated.model.skin.ISkinPart;
import cc.fascinated.model.skin.Skin;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.awt.*;
import java.awt.image.BufferedImage;

@AllArgsConstructor @Getter @Log4j2
public class BodyRenderer extends SkinRenderer<ISkinPart.Custom> {
    public static final BodyRenderer INSTANCE = new BodyRenderer();

    @Override
    public BufferedImage render(Skin skin, ISkinPart.Custom part, boolean renderOverlays, int size) {
        BufferedImage texture = new BufferedImage(16, 32, BufferedImage.TYPE_INT_ARGB); // The texture to return
        Graphics2D graphics = texture.createGraphics(); // Create the graphics for drawing

        // Get the Vanilla skin parts to draw
        BufferedImage face = getVanillaSkinPart(skin, ISkinPart.Vanilla.FACE, -1);
        BufferedImage body = getVanillaSkinPart(skin, ISkinPart.Vanilla.BODY_FRONT, -1);
        BufferedImage leftArm = getVanillaSkinPart(skin, ISkinPart.Vanilla.LEFT_ARM_FRONT, -1);
        BufferedImage rightArm = getVanillaSkinPart(skin, ISkinPart.Vanilla.RIGHT_ARM_FRONT, -1);
        BufferedImage leftLeg = getVanillaSkinPart(skin, ISkinPart.Vanilla.LEFT_LEG_FRONT, -1);
        BufferedImage rightLeg = getVanillaSkinPart(skin, ISkinPart.Vanilla.RIGHT_LEG_FRONT, -1);

        // Draw the body parts
        graphics.drawImage(face, 4, 0, null);
        graphics.drawImage(body, 4, 8, null);
        graphics.drawImage(leftArm, skin.getModel() == Skin.Model.SLIM ? 1 : 0, 8, null);
        graphics.drawImage(rightArm, 12, 8, null);
        graphics.drawImage(leftLeg, 8, 20, null);
        graphics.drawImage(rightLeg, 4, 20, null);

        graphics.dispose();
        return ImageUtils.resize(texture, (double) size / 32);
    }
}
