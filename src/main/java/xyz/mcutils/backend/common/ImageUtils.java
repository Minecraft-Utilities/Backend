package xyz.mcutils.backend.common;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Iterator;

@Log4j2(topic = "Image Utils")
public class ImageUtils {
    /**
     * Scale the given image to the provided scale.
     *
     * @param image the image to scale
     * @param scale  the scale to scale the image to
     * @return the scaled image
     */
    public static BufferedImage resize(BufferedImage image, double scale) {
        BufferedImage scaled = new BufferedImage((int) (image.getWidth() * scale), (int) (image.getHeight() * scale), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.drawImage(image, AffineTransform.getScaleInstance(scale, scale), null);
        graphics.dispose();
        return scaled;
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
    public static void copyRect(BufferedImage img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2, double scale) {
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
        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                int sx = sox + (flipX ? w - 1 - dx : dx);
                int sy = soy + (flipY ? h - 1 - dy : dy);
                if (sx >= 0 && sx < img.getWidth() && sy >= 0 && sy < img.getHeight()) {
                    int pixel = img.getRGB(sx, sy);
                    int tx = dox + dx, ty = doy + dy;
                    if (tx >= 0 && tx < img.getWidth() && ty >= 0 && ty < img.getHeight()) {
                        img.setRGB(tx, ty, pixel);
                    }
                }
            }
        }
    }

    public static void setAreaTransparentIfOpaque(BufferedImage img, int x1, int y1, int x2, int y2, double scale) {
        int xMin = (int) (Math.min(x1, x2) * scale), xMax = (int) (Math.max(x1, x2) * scale);
        int yMin = (int) (Math.min(y1, y2) * scale), yMax = (int) (Math.max(y1, y2) * scale);
        boolean hasTransparency = false;
        for (int y = yMin; y < yMax && y < img.getHeight(); y++) {
            for (int x = xMin; x < xMax && x < img.getWidth(); x++) {
                if (((img.getRGB(x, y) >> 24) & 0xFF) < 128) {
                    hasTransparency = true;
                    break;
                }
            }
            if (hasTransparency) break;
        }
        if (hasTransparency) return;
        for (int y = yMin; y < yMax && y < img.getHeight(); y++) {
            for (int x = xMin; x < xMax && x < img.getWidth(); x++) {
                img.setRGB(x, y, img.getRGB(x, y) & 0x00_FFFFFF);
            }
        }
    }

    /**
     * Convert an image to bytes (PNG). Uses explicit ImageWriter with lower compression
     * for faster encoding; encoding speed depends on the ImageIO PNG implementation.
     *
     * @param image the image to convert
     * @return the image as bytes
     */
    @SneakyThrows
    public static byte[] imageToBytes(BufferedImage image) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
        if (!writers.hasNext()) {
            try (ByteArrayOutputStream fallback = new ByteArrayOutputStream()) {
                ImageIO.write(image, "png", fallback);
                return fallback.toByteArray();
            } catch (Exception e) {
                throw new Exception("Image could not be converted to bytes", e);
            }
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputStream)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.2f);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return outputStream.toByteArray();
    }

    /**
     * Convert a base64 string to an image.
     *
     * @param base64 the base64 string to convert
     * @return the image
     */
    @SneakyThrows
    public static BufferedImage base64ToImage(String base64) {
        String favicon = base64.contains("data:image/png;base64,") ? base64.split(",")[1] : base64;

        try {
            return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(favicon)));
        } catch (Exception e) {
            throw new Exception("Base64 could not be converted to image", e);
        }
    }
}
