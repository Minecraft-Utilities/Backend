package xyz.mcutils.backend.common;

import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import xyz.mcutils.backend.common.renderer.texture.Coordinates;
import xyz.mcutils.backend.common.renderer.texture.PlayerModelCoordinates;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;

@Slf4j
public class SkinUtils {
    /**
     * Upgrades a legacy 64×32 skin to 64×64 (1.8 format) if needed.
     *
     * @param textureId the texture id of the skin being upgraded
     * @return PNG bytes (64×64 if input was 64×32, otherwise unchanged)
     */
    public static byte[] upgradeLegacySkin(String textureId, byte[] skinImage) {
        try {
            BufferedImage image = ImageUtils.decodeImage(skinImage);
            if (image.getWidth() != 64 || image.getHeight() != 32) {
                return skinImage;
            }
            long start = System.currentTimeMillis();
            BufferedImage upgraded = upgradeLegacySkin(image);
            byte[] bytes = ImageUtils.imageToBytes(upgraded);
            log.debug("Upgraded legacy skin '{}' in {}ms", textureId, System.currentTimeMillis() - start);
            return bytes;
        } catch (Exception e) {
            log.warn("Could not upgrade legacy skin, using original: {}", e.getMessage());
            return skinImage;
        }
    }

    /**
     * Upgrades a legacy 64×32 Minecraft skin to the modern 64×64 format (1.8+).
     * Legacy skins may or may not have overlays. Places the 64×32 in the top half,
     * mirrors the leg/arm to create the missing left leg and left arm base, then
     * clears only overlay regions that are empty (transparent/black) so they don't render black.
     *
     * @param image the skin image (legacy 64×32 or already 64×64)
     * @return the image in 64×64 format
     */
    public static BufferedImage upgradeLegacySkin(@NotNull BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        if (w == h) {
            return image;
        }
        if (h * 2 != w) {
            return image;
        }
        double scale = w / 64.0;
        int newH = h + (int) (32 * scale);
        BufferedImage upgraded = new BufferedImage(w, newH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = upgraded.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();

        // Clear only empty overlay regions (transparent/black) so they don't render black; preserve drawn overlays
        for (int[] rect : PlayerModelCoordinates.LegacyUpgrade.CLEAR_RECTS) {
            ImageUtils.fillTransparentIfEmpty(upgraded, rect[0], rect[1], rect[2], rect[3], scale);
        }

        // Create missing left leg and left arm (mirror from legacy right leg/arm)
        for (int[] rect : PlayerModelCoordinates.LegacyUpgrade.LEFT_LEG_COPIES) {
            ImageUtils.copyRect(upgraded, rect[0], rect[1], rect[2], rect[3], rect[4], rect[5], rect[6], rect[7], scale);
        }
        for (int[] rect : PlayerModelCoordinates.LegacyUpgrade.LEFT_ARM_COPIES) {
            ImageUtils.copyRect(upgraded, rect[0], rect[1], rect[2], rect[3], rect[4], rect[5], rect[6], rect[7], scale);
        }

        return upgraded;
    }

    /**
     * If the given skin texture is fully transparent (no visible pixels), fills all base-layer
     * skin regions with opaque black so renders produce a visible silhouette rather than nothing.
     * Overlay regions are left untouched.
     *
     * @param skinBytes the raw PNG bytes of the skin texture
     * @return the (possibly modified) PNG bytes
     */
    public static byte[] fixTransparentSkin(byte[] skinBytes) {
        BufferedImage skinImage = ImageUtils.decodeImage(skinBytes);
        if (!ImageUtils.isFullyTransparent(skinImage)) {
            return skinBytes;
        }
        fillBaseLayerBlack(skinImage);
        return ImageUtils.imageToBytes(skinImage);
    }

    /**
     * Fills every base-layer skin region (head, body, arms, legs — not overlays) with fully
     * opaque black. Overlay regions are not modified.
     *
     * @param skinImage the 64×64 skin image to modify in-place
     */
    private static void fillBaseLayerBlack(BufferedImage skinImage) {
        for (PlayerModelCoordinates.Skin part : PlayerModelCoordinates.Skin.values()) {
            if (part.getOverlays().length == 0) {
                continue;
            }
            Coordinates c = part.getCoordinates();
            int w = c.width();
            int h = c.height();
            int[] pixels = new int[w * h];
            Arrays.fill(pixels, 0xFF000000);
            skinImage.setRGB(c.x(), c.y(), w, h, pixels, 0, w);
        }
    }
}
