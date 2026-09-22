package xyz.mcutils.backend.common.renderer;

import org.junit.jupiter.api.Test;
import xyz.mcutils.backend.common.math.Vector3;
import xyz.mcutils.backend.common.renderer.model.impl.PlayerHeadModel;
import xyz.mcutils.backend.common.renderer.model.impl.PlayerModel;
import xyz.mcutils.backend.common.renderer.raster.Isometric3DRenderer;
import xyz.mcutils.backend.model.domain.skin.Skin;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the texture orientation of the isometric skin renders: a texture region must be drawn
 * the way it is painted in the skin file, never mirrored.
 * <p>
 * Every case renders a skin whose visible texture regions are split into a distinct left and
 * right half, so a mirrored mapping is observable as the halves swapping sides.
 */
class IsometricSkinOrientationTest {
    /** Left/right halves of the head's face rect, which the front views show. */
    private static final Color FACE_LEFT = new Color(255, 0, 0);
    private static final Color FACE_RIGHT = new Color(0, 0, 255);
    /** Left/right halves of the back-of-head rect, which the back view shows. */
    private static final Color BACK_LEFT = new Color(0, 255, 0);
    private static final Color BACK_RIGHT = new Color(255, 255, 0);
    /** Left/right halves of the hat (second layer) face rect, which the front views show. */
    private static final Color HAT_LEFT = new Color(255, 0, 255);
    private static final Color HAT_RIGHT = new Color(0, 255, 255);

    // Head view, mirroring HeadIsoRenderer's constants.
    private static final Vector3 HEAD_EYE = new Vector3(0, 28, -20);
    private static final Vector3 HEAD_TARGET = new Vector3(0, 28, 0);
    private static final double HEAD_YAW = 225;
    private static final double HEAD_PITCH = -35;

    // Body views, mirroring FullBodyIsoRendererBase's constants (FRONT yaw + 225, BACK yaw + 45).
    private static final Vector3 BODY_EYE = new Vector3(0, 28, -45);
    private static final Vector3 BODY_TARGET = new Vector3(0, 16.5, 0);
    private static final double BODY_PITCH = -20;
    private static final double BODY_ASPECT_RATIO = 512.0 / 869.0;

    @Test
    void headIsoDrawsTheHeadFaceUnmirrored() throws Exception {
        BufferedImage rendered = renderHead(syntheticSkin(), false);

        assertLeftHalfLeftOfRightHalf(rendered, FACE_LEFT, FACE_RIGHT, "head iso");
    }

    @Test
    void headIsoDrawsTheHatLayerUnmirrored() throws Exception {
        BufferedImage rendered = renderHead(syntheticSkin(), true);

        assertLeftHalfLeftOfRightHalf(rendered, HAT_LEFT, HAT_RIGHT, "head iso with hat layer");
    }

    @Test
    void fullBodyFrontDrawsTheHeadFaceUnmirrored() throws Exception {
        BufferedImage rendered = renderBody(syntheticSkin(), HEAD_YAW);

        assertLeftHalfLeftOfRightHalf(rendered, FACE_LEFT, FACE_RIGHT, "full body front");
    }

    @Test
    void fullBodyBackDrawsTheBackOfTheHeadUnmirrored() throws Exception {
        // The back of the head (24, 8) is the only textured region visible from behind.
        BufferedImage rendered = renderBody(syntheticSkin(), 45);

        assertLeftHalfLeftOfRightHalf(rendered, BACK_LEFT, BACK_RIGHT, "full body back");
    }

    private static BufferedImage renderHead(BufferedImage skinImage, boolean renderOverlays) throws Exception {
        Skin skin = skin();
        return Isometric3DRenderer.INSTANCE.render(
                skinImage,
                PlayerHeadModel.buildFaces(skin, renderOverlays),
                new Isometric3DRenderer.ViewParams(HEAD_EYE, HEAD_TARGET, HEAD_YAW, HEAD_PITCH, 1.0),
                256);
    }

    private static BufferedImage renderBody(BufferedImage skinImage, double yawDeg) throws Exception {
        Skin skin = skin();
        return Isometric3DRenderer.INSTANCE.render(
                skinImage,
                PlayerModel.buildFaces(skin, false),
                new Isometric3DRenderer.ViewParams(BODY_EYE, BODY_TARGET, yawDeg, BODY_PITCH, BODY_ASPECT_RATIO),
                256);
    }

    /**
     * Asserts the rendered left-half texels sit left of the right-half texels. The pixel counts are
     * asserted first so a render that draws nothing cannot pass vacuously.
     */
    private static void assertLeftHalfLeftOfRightHalf(BufferedImage rendered, Color leftHalf, Color rightHalf, String view) {
        double[] left = meanX(rendered, leftHalf);
        double[] right = meanX(rendered, rightHalf);
        assertTrue(left[1] > 0, view + ": no left-half texels were rendered");
        assertTrue(right[1] > 0, view + ": no right-half texels were rendered");
        assertTrue(left[0] < right[0],
                "%s: texture is mirrored (left-half mean x %.1f, right-half mean x %.1f)".formatted(view, left[0], right[0]));
    }

    /**
     * Mean x of the pixels matching the given colour, plus their count, as {@code {meanX, count}}.
     * Colour channels are compared instead of exact values because the renderer shades every face.
     */
    private static double[] meanX(BufferedImage image, Color colour) {
        double sum = 0;
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                if (((pixel >>> 24) & 0xFF) == 0) {
                    continue;
                }
                int red = (pixel >> 16) & 0xFF;
                int green = (pixel >> 8) & 0xFF;
                int blue = pixel & 0xFF;
                if (matches(red, green, blue, colour)) {
                    sum += x;
                    count++;
                }
            }
        }
        return new double[]{count == 0 ? 0 : sum / count, count};
    }

    /**
     * True when every channel the colour sets is at least 40 brighter than every channel it leaves
     * unset, so a shaded texel still matches the colour it was painted with.
     */
    private static boolean matches(int red, int green, int blue, Color colour) {
        int[] channels = {red, green, blue};
        int[] wanted = {colour.getRed(), colour.getGreen(), colour.getBlue()};
        for (int on = 0; on < channels.length; on++) {
            for (int off = 0; off < channels.length; off++) {
                if (wanted[on] > 127 && wanted[off] < 128 && channels[on] < channels[off] + 40) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Skin with only the head's face rect (8, 8), hat face rect (40, 8) and back rect (24, 8)
     * painted, each split into a left and a right half. Everything else stays transparent so each
     * view shows one region.
     */
    private static BufferedImage syntheticSkin() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0, 0, 0, 0));
        graphics.fillRect(0, 0, 64, 64);
        fill(graphics, 8, 8, 4, 8, FACE_LEFT);
        fill(graphics, 12, 8, 4, 8, FACE_RIGHT);
        fill(graphics, 40, 8, 4, 8, HAT_LEFT);
        fill(graphics, 44, 8, 4, 8, HAT_RIGHT);
        fill(graphics, 24, 8, 4, 8, BACK_LEFT);
        fill(graphics, 28, 8, 4, 8, BACK_RIGHT);
        graphics.dispose();
        return image;
    }

    private static void fill(Graphics2D graphics, int x, int y, int w, int h, Color colour) {
        graphics.setColor(colour);
        graphics.fillRect(x, y, w, h);
    }

    /**
     * Skin with the model set. {@code model} has no setter and the id constructor needs a started
     * application context, so it is set directly.
     */
    private static Skin skin() throws Exception {
        Skin skin = new Skin();
        Field model = Skin.class.getDeclaredField("model");
        model.setAccessible(true);
        model.set(skin, Skin.Model.DEFAULT);
        return skin;
    }
}
