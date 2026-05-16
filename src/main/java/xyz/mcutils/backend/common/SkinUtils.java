package xyz.mcutils.backend.common;

import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
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

        for (int[] rect : PlayerModelCoordinates.LegacyUpgrade.CLEAR_RECTS) {
            ImageUtils.fillTransparentIfEmpty(upgraded, rect[0], rect[1], rect[2], rect[3], scale);
        }

        for (int[] rect : PlayerModelCoordinates.LegacyUpgrade.LEFT_LEG_COPIES) {
            ImageUtils.copyRect(upgraded, rect[0], rect[1], rect[2], rect[3], rect[4], rect[5], rect[6], rect[7], scale);
        }
        for (int[] rect : PlayerModelCoordinates.LegacyUpgrade.LEFT_ARM_COPIES) {
            ImageUtils.copyRect(upgraded, rect[0], rect[1], rect[2], rect[3], rect[4], rect[5], rect[6], rect[7], scale);
        }

        return upgraded;
    }

    /**
     * If any base-layer skin region is fully transparent, fills that region with opaque black
     * so renders produce a visible silhouette rather than nothing.
     * Overlay regions are left untouched.
     *
     * @param skinBytes the raw PNG bytes of the skin texture
     * @return the (possibly modified) PNG bytes
     */
    public static byte[] fixTransparentSkin(byte[] skinBytes) {
        BufferedImage skinImage = ImageUtils.decodeImage(skinBytes);
        if (!fillMissingBaseLayerRegions(skinImage)) {
            return skinBytes;
        }
        return ImageUtils.imageToBytes(skinImage);
    }

    /**
     * Fills any base-layer skin region (head, body, arms, legs — not overlays) that is fully
     * transparent with opaque black. Overlay regions are not modified.
     *
     * @param skinImage the 64×64 skin image to modify in-place
     * @return true if any region was modified, false if all regions already had visible pixels
     */
    private static boolean fillMissingBaseLayerRegions(BufferedImage skinImage) {
        boolean modified = false;
        for (PlayerModelCoordinates.ModelBox box : PlayerModelCoordinates.ModelBox.values()) {
            int[] uv = box.getBaseUv(false);
            int x = uv[0], y = uv[1], sizeX = uv[2], sizeY = uv[3], sizeZ = uv[4];

            int[][] faces = {
                    {x,                 y,          sizeX, sizeY},  // front
                    {x + sizeX + sizeZ, y,          sizeX, sizeY},  // back
                    {x - sizeZ,         y,          sizeZ, sizeY},  // side A
                    {x + sizeX,         y,          sizeZ, sizeY},  // side B
                    {x,                 y - sizeZ,  sizeX, sizeZ},  // top
                    {x + sizeX,         y - sizeZ,  sizeX, sizeZ},  // bottom
            };

            for (int[] face : faces) {
                if (ImageUtils.isRegionFullyTransparent(skinImage, face[0], face[1], face[2], face[3])) {
                    fillRect(skinImage, face[0], face[1], face[2], face[3]);
                    modified = true;
                }
            }
        }
        return modified;
    }

    private static void fillRect(BufferedImage image, int x, int y, int w, int h) {
        int[] pixels = new int[w * h];
        Arrays.fill(pixels, 0xFF000000);
        image.setRGB(x, y, w, h, pixels, 0, w);
    }
}