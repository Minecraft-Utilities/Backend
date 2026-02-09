package xyz.mcutils.backend.common;

import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import xyz.mcutils.backend.common.renderer.texture.PlayerModelCoordinates;

import java.awt.*;
import java.awt.image.BufferedImage;

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
     * Legacy skins have no overlays — only base layer. Places the 64×32 in the top
     * half, mirrors the leg/arm to create the missing left leg and left arm base,
     * then clears all overlay regions to transparent.
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

        // Create missing left leg and left arm (mirror from legacy right leg/arm)
        for (int[] rect : PlayerModelCoordinates.LegacyUpgrade.LEFT_LEG_COPIES) {
            ImageUtils.copyRect(upgraded, rect[0], rect[1], rect[2], rect[3], rect[4], rect[5], rect[6], rect[7], scale);
        }
        for (int[] rect : PlayerModelCoordinates.LegacyUpgrade.LEFT_ARM_COPIES) {
            ImageUtils.copyRect(upgraded, rect[0], rect[1], rect[2], rect[3], rect[4], rect[5], rect[6], rect[7], scale);
        }

        // Clear overlay regions (HEADZ, BODYZ, RAZ, LAZ, LLZ) — legacy skins have no overlays
        for (int[] region : PlayerModelCoordinates.LegacyUpgrade.CLEAR_OVERLAYS) {
            ImageUtils.setAreaTransparentIfOpaque(upgraded, region[0], region[1], region[2], region[3], scale);
        }

        return upgraded;
    }
}
