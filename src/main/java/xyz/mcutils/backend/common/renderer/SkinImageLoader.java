package xyz.mcutils.backend.common.renderer;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

/**
 * Loads skin images for 3D renderers. Ensures 64×64 format.
 */
public final class SkinImageLoader {

    private SkinImageLoader() {}

    /**
     * Loads a skin image from bytes and normalizes it to 64×64.
     *
     * @param skinBytes PNG bytes (e.g. from SkinService.getSkinImage(skin, true))
     * @return 64×64 ARGB image, never null
     * @throws java.io.IOException if image cannot be read
     */
    public static BufferedImage load64x64(byte[] skinBytes) throws java.io.IOException {
        BufferedImage skinImage = ImageIO.read(new ByteArrayInputStream(skinBytes));
        if (skinImage == null) {
            throw new IllegalStateException("Failed to load skin image");
        }
        if (skinImage.getWidth() != 64 || skinImage.getHeight() != 64) {
            BufferedImage normalized = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = normalized.createGraphics();
            g.drawImage(skinImage, 0, 0, 64, 64, null);
            g.dispose();
            skinImage = normalized;
        }
        return skinImage;
    }
}
