package xyz.mcutils.backend.common;

import com.pngencoder.PngEncoder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.stream.IntStream;

@Slf4j
public class ImageUtils {

    /**
     * Scales the image by the given factor using nearest-neighbor sampling.
     * Fast but can look blocky when downscaling; for smoother downscaling use
     * {@link #resizeToHeight(BufferedImage, int)}.
     *
     * @param image the image to scale
     * @param scale scale factor (e.g. 0.5 for half size, 2.0 for double)
     * @return a new image with dimensions (width*scale, height*scale)
     */
    public static BufferedImage resize(BufferedImage image, double scale) {
        int w = image.getWidth();
        int h = image.getHeight();
        int newWidth = Math.max(1, (int) (w * scale));
        int newHeight = Math.max(1, (int) (h * scale));
        BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        int[] srcPixels;
        if (image.getRaster().getDataBuffer() instanceof DataBufferInt db) {
            srcPixels = db.getData();
        } else {
            BufferedImage tmp = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = tmp.createGraphics();
            g.drawImage(image, 0, 0, null);
            g.dispose();
            srcPixels = ((DataBufferInt) tmp.getRaster().getDataBuffer()).getData();
        }
        int[] destPixels = ((DataBufferInt) scaled.getRaster().getDataBuffer()).getData();
        final long xStep = (newWidth > 1) ? ((long) w << 32) / newWidth : 0;
        final long yStep = (newHeight > 1) ? ((long) h << 32) / newHeight : 0;
        final int sw = w, sh = h;
        IntStream.range(0, newHeight).parallel().forEach(y -> {
            int srcY = (int) ((y * yStep) >> 32);
            if (srcY >= sh) srcY = sh - 1;
            int srcRow = srcY * sw;
            int destRow = y * newWidth;
            long srcX = 0;
            for (int x = 0; x < newWidth; x++) {
                int sx = (int) (srcX >> 32);
                if (sx >= sw) sx = sw - 1;
                destPixels[destRow + x] = srcPixels[srcRow + sx];
                srcX += xStep;
            }
        });
        return scaled;
    }

    /**
     * Resizes so height equals targetHeight. Uses nearest-neighbor for both up and down
     * (fast). For smoother downscaling use {@link #resizeToHeightSmooth(BufferedImage, int)}.
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
        return resize(image, scale);
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
        return imageToBytes(image, 6);
    }

    /**
     * Encodes the image as PNG bytes with the given compression level (0–9).
     * Lower levels (1–2) are much faster with slightly larger output; use for render output.
     *
     * @param image            the image to encode
     * @param compressionLevel 0 (none) to 9 (max); 1 is a good tradeoff for speed
     * @return the PNG bytes
     */
    @SneakyThrows
    public static byte[] imageToBytes(BufferedImage image, int compressionLevel) {
        return new PngEncoder()
                .withBufferedImage(image)
                .withCompressionLevel(compressionLevel)
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
