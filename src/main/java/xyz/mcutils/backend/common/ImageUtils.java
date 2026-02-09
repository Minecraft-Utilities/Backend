package xyz.mcutils.backend.common;

import com.pngencoder.PngEncoder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

@Slf4j
public class ImageUtils {

    /**
     * Scales the image by the given factor using nearest-neighbor sampling.
     * Fast but can look blocky when downscaling; for smoother downscaling use
     * {@link #resizeToHeight(BufferedImage, int)} or {@link #resizeSmooth(BufferedImage, double)}.
     *
     * @param image the image to scale
     * @param scale scale factor (e.g. 0.5 for half size, 2.0 for double)
     * @return a new image with dimensions (width*scale, height*scale)
     */
    public static BufferedImage resize(BufferedImage image, double scale) {
        int newWidth = (int) (image.getWidth() * scale);
        int newHeight = (int) (image.getHeight() * scale);
        BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);

        // Direct pixel manipulation - fastest for nearest-neighbor
        int[] srcPixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        int[] destPixels = new int[newWidth * newHeight];

        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                int srcX = (int) (x / scale);
                int srcY = (int) (y / scale);
                destPixels[y * newWidth + x] = srcPixels[srcY * image.getWidth() + srcX];
            }
        }

        scaled.setRGB(0, 0, newWidth, newHeight, destPixels, 0, newWidth);
        return scaled;
    }

    /**
     * Resizes the image so its height equals the target height, preserving aspect ratio.
     * When downscaling (targetHeight &lt; current height), uses bilinear interpolation with
     * premultiplied alpha for smooth edges without fringing. When upscaling, uses
     * nearest-neighbor. Returns the original image unchanged if height already matches.
     *
     * @param image        the image to resize
     * @param targetHeight the desired height in pixels
     * @return the resized image, or the same instance if height already equals targetHeight
     */
    public static BufferedImage resizeToHeight(BufferedImage image, int targetHeight) {
        int h = image.getHeight();
        if (targetHeight == h) {
            return image;
        }
        double scale = (double) targetHeight / h;
        if (scale < 1.0) {
            return resizeSmooth(image, scale);
        }
        return resize(image, scale);
    }

    /**
     * Scales the image by the given factor using bilinear interpolation.
     * Converts to premultiplied alpha before scaling and back to straight alpha after,
     * so transparent edges do not show color fringing.
     *
     * @param image the image to scale
     * @param scale scale factor (e.g. 0.5 for half size)
     * @return a new scaled image
     */
    public static BufferedImage resizeSmooth(BufferedImage image, double scale) {
        int newWidth = Math.max(1, (int) (image.getWidth() * scale));
        int newHeight = Math.max(1, (int) (image.getHeight() * scale));
        BufferedImage premul = toPremultipliedAlpha(image);
        AffineTransform at = AffineTransform.getScaleInstance(scale, scale);
        AffineTransformOp op = new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
        BufferedImage result = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        op.filter(premul, result);
        fromPremultipliedAlpha(result);
        return result;
    }

    /**
     * Converts the image from straight alpha to premultiplied alpha (R,G,B multiplied by A/255).
     * Returns a new image; the original is unchanged. Used internally before bilinear scaling
     * so that blending with transparent pixels does not produce fringing.
     *
     * @param image the image to convert
     * @return a new image with premultiplied alpha
     */
    private static BufferedImage toPremultipliedAlpha(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
        for (int i = 0; i < pixels.length; i++) {
            int a = (pixels[i] >> 24) & 0xff;
            int r = (pixels[i] >> 16) & 0xff;
            int g = (pixels[i] >> 8) & 0xff;
            int b = pixels[i] & 0xff;
            if (a < 255) {
                r = (r * a + 127) / 255;
                g = (g * a + 127) / 255;
                b = (b * a + 127) / 255;
            }
            pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, pixels, 0, w);
        return out;
    }

    /**
     * Converts the image from premultiplied alpha back to straight alpha in place (R,G,B = R',G',B' * 255/A).
     * Called after bilinear scaling to restore standard ARGB for output.
     *
     * @param image the image to convert in place
     */
    private static void fromPremultipliedAlpha(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
        for (int i = 0; i < pixels.length; i++) {
            int a = (pixels[i] >> 24) & 0xff;
            if (a == 0) {
                pixels[i] = 0;
                continue;
            }
            int r = (pixels[i] >> 16) & 0xff;
            int g = (pixels[i] >> 8) & 0xff;
            int b = pixels[i] & 0xff;
            r = (r * 255 + a / 2) / a;
            g = (g * 255 + a / 2) / a;
            b = (b * 255 + a / 2) / a;
            if (r > 255) r = 255;
            if (g > 255) g = 255;
            if (b > 255) b = 255;
            pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        image.setRGB(0, 0, w, h, pixels, 0, w);
    }

    /**
     * Decodes raw image bytes (e.g. PNG) into a BufferedImage.
     * Supports any format that {@link javax.imageio.ImageIO#read} can read.
     *
     * @param bytes the image bytes (e.g. PNG, JPEG)
     * @return the decoded image, never null
     * @throws IllegalStateException if decoding fails or the format is not recognized
     */
    public static BufferedImage decodeImage(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new IllegalStateException("Failed to decode image");
            }
            return image;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to decode image", e);
        }
    }

    /**
     * Copies a rectangle from source to dest in 64×64 skin space (coords 0–64), with optional flip.
     * Rectangles are (min(dx1,dx2), min(dy1,dy2)) to (max(dx1,dx2), max(dy1,dy2)); same for source.
     * If dx1 &gt; dx2 or dy1 &gt; dy2, the copy is flipped so the arm/leg is mirrored.
     *
     * @param img   image to modify
     * @param dx1   dest corner x
     * @param dy1   dest corner y
     * @param dx2   dest opposite corner x
     * @param dy2   dest opposite corner y
     * @param sx1   source corner x
     * @param sy1   source corner y
     * @param sx2   source opposite corner x
     * @param sy2   source opposite corner y
     * @param scale scale factor (e.g. 1 for 64×32→64×64)
     */
    public static void copyRect(BufferedImage img, int dx1, int dy1, int dx2, int dy2,
                                int sx1, int sy1, int sx2, int sy2, double scale) {
        int dxMin = Math.min(dx1, dx2), dxMax = Math.max(dx1, dx2);
        int dyMin = Math.min(dy1, dy2), dyMax = Math.max(dy1, dy2);
        int sxMin = Math.min(sx1, sx2), sxMax = Math.max(sx1, sx2);
        int syMin = Math.min(sy1, sy2), syMax = Math.max(sy1, sy2);
        int sw = sxMax - sxMin, sh = syMax - syMin;
        int dw = dxMax - dxMin, dh = dyMax - dyMin;
        if (sw != dw || sh != dh) return;

        int sox = (int) (sxMin * scale), soy = (int) (syMin * scale);
        int dox = (int) (dxMin * scale), doy = (int) (dyMin * scale);
        int w = Math.max(1, (int) (sw * scale)), h = Math.max(1, (int) (sh * scale));
        boolean flipX = dx1 > dx2, flipY = dy1 > dy2;

        // Extract source region once
        int[] srcPixels = img.getRGB(sox, soy, w, h, null, 0, w);
        int[] destPixels = new int[w * h];

        // Apply flips in memory
        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                int srcIdx = (flipY ? h - 1 - dy : dy) * w + (flipX ? w - 1 - dx : dx);
                destPixels[dy * w + dx] = srcPixels[srcIdx];
            }
        }

        // Write back once
        img.setRGB(dox, doy, w, h, destPixels, 0, w);
    }

    /**
     * Sets a rectangular area to fully transparent if the entire area is currently opaque.
     * If any pixel in the area has alpha < 128, no changes are made.
     *
     * @param img   the image to modify in-place
     * @param x1    first corner x-coordinate in skin space
     * @param y1    first corner y-coordinate in skin space
     * @param x2    opposite corner x-coordinate in skin space
     * @param y2    opposite corner y-coordinate in skin space
     * @param scale scale factor applied to coordinates
     */
    public static void setAreaTransparentIfOpaque(BufferedImage img, int x1, int y1, int x2, int y2, double scale) {
        int xMin = (int) (Math.min(x1, x2) * scale), xMax = (int) (Math.max(x1, x2) * scale);
        int yMin = (int) (Math.min(y1, y2) * scale), yMax = (int) (Math.max(y1, y2) * scale);
        int w = xMax - xMin, h = yMax - yMin;

        // Get pixels once
        int[] pixels = img.getRGB(xMin, yMin, w, h, null, 0, w);

        // Check for transparency
        boolean hasTransparency = false;
        for (int pixel : pixels) {
            if (((pixel >> 24) & 0xFF) < 128) {
                hasTransparency = true;
                break;
            }
        }

        if (!hasTransparency) {
            // Make transparent
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] &= 0x00_FFFFFF;
            }
            img.setRGB(xMin, yMin, w, h, pixels, 0, w);
        }
    }

    /**
     * Encodes the image as PNG bytes. Uses PngEncoder for faster encoding than ImageIO.
     *
     * @param image the image to encode
     * @return the PNG bytes
     */
    @SneakyThrows
    public static byte[] imageToBytes(BufferedImage image) {
        return new PngEncoder()
                .withBufferedImage(image)
                .withCompressionLevel(6)
                .toBytes();
    }

    /**
     * Decodes a base64-encoded image string into a BufferedImage.
     * Accepts optional {@code data:image/png;base64,} prefix and strips whitespace
     * (e.g. newlines in server favicon strings).
     *
     * @param base64 the base64 string (optionally with data URL prefix)
     * @return the decoded image
     * @throws Exception if the string is not valid base64 or cannot be decoded as an image
     */
    @SneakyThrows
    public static BufferedImage base64ToImage(String base64) {
        String favicon = base64.contains("data:image/png;base64,") ? base64.split(",", 2)[1] : base64;
        // Strip whitespace (newlines, spaces) - some Minecraft servers send favicon with line breaks
        favicon = favicon.replaceAll("\\s+", "");

        try {
            return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(favicon)));
        } catch (Exception e) {
            throw new Exception("Base64 could not be converted to image", e);
        }
    }
}
