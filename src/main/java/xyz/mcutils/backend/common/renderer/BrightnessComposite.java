package xyz.mcutils.backend.common.renderer;

import java.awt.*;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

/**
 * A composite that multiplies source RGB by a scalar (brightness) before blending
 * onto the destination. Used by the isometric renderer to shade faces without
 * allocating intermediate images.
 */
public final class BrightnessComposite implements Composite {
    private final float brightness;

    /**
     * @param brightness scalar in [0, 1]; 1.0 leaves RGB unchanged
     */
    public BrightnessComposite(double brightness) {
        this.brightness = (float) Math.max(0, Math.min(1, brightness));
    }

    @Override
    public CompositeContext createContext(ColorModel srcColorModel, ColorModel dstColorModel, RenderingHints hints) {
        return new BrightnessContext(brightness);
    }

    private static final class BrightnessContext implements CompositeContext {
        private final float brightness;

        BrightnessContext(float brightness) {
            this.brightness = brightness;
        }

        @Override
        public void compose(Raster src, Raster dstIn, WritableRaster dstOut) {
            int w = Math.min(src.getWidth(), Math.min(dstIn.getWidth(), dstOut.getWidth()));
            int h = Math.min(src.getHeight(), Math.min(dstIn.getHeight(), dstOut.getHeight()));
            if (w <= 0 || h <= 0) return;
            int srcMinX = src.getMinX();
            int srcMinY = src.getMinY();
            int dstInMinX = dstIn.getMinX();
            int dstInMinY = dstIn.getMinY();
            int dstOutMinX = dstOut.getMinX();
            int dstOutMinY = dstOut.getMinY();

            if (src.getNumBands() == 1 && src.getTransferType() == DataBuffer.TYPE_INT
                    && dstIn.getNumBands() == 1 && dstIn.getTransferType() == DataBuffer.TYPE_INT
                    && dstOut.getNumBands() == 1 && dstOut.getTransferType() == DataBuffer.TYPE_INT) {
                composePackedInt(src, dstIn, dstOut, w, h,
                        srcMinX, srcMinY, dstInMinX, dstInMinY, dstOutMinX, dstOutMinY);
                return;
            }
            composePerPixel(src, dstIn, dstOut, w, h,
                    srcMinX, srcMinY, dstInMinX, dstInMinY, dstOutMinX, dstOutMinY);
        }

        private void composePackedInt(Raster src, Raster dstIn, WritableRaster dstOut,
                                      int w, int h,
                                      int srcMinX, int srcMinY, int dstInMinX, int dstInMinY,
                                      int dstOutMinX, int dstOutMinY) {
            int[] srcRow = new int[w];
            int[] dstRow = new int[w];
            for (int y = 0; y < h; y++) {
                src.getSamples(srcMinX, srcMinY + y, w, 1, 0, srcRow);
                dstIn.getSamples(dstInMinX, dstInMinY + y, w, 1, 0, dstRow);
                for (int x = 0; x < w; x++) {
                    int pixel = srcRow[x];
                    int sa = (pixel >> 24) & 0xFF;
                    int r = (pixel >> 16) & 0xFF;
                    int g = (pixel >> 8) & 0xFF;
                    int b = pixel & 0xFF;
                    r = (int) (r * brightness);
                    g = (int) (g * brightness);
                    b = (int) (b * brightness);
                    if (sa < 255) {
                        int invSa = 255 - sa;
                        int dp = dstRow[x];
                        int dr = (dp >> 16) & 0xFF;
                        int dg = (dp >> 8) & 0xFF;
                        int db = dp & 0xFF;
                        int da = (dp >> 24) & 0xFF;
                        r = (r * sa + dr * invSa) / 255;
                        g = (g * sa + dg * invSa) / 255;
                        b = (b * sa + db * invSa) / 255;
                        sa = sa + (255 - sa) * da / 255;
                    }
                    dstRow[x] = (Math.min(255, Math.max(0, sa)) << 24)
                            | (Math.min(255, Math.max(0, r)) << 16)
                            | (Math.min(255, Math.max(0, g)) << 8)
                            | Math.min(255, Math.max(0, b));
                }
                dstOut.setSamples(dstOutMinX, dstOutMinY + y, w, 1, 0, dstRow);
            }
        }

        private void composePerPixel(Raster src, Raster dstIn, WritableRaster dstOut,
                                      int w, int h,
                                      int srcMinX, int srcMinY, int dstInMinX, int dstInMinY,
                                      int dstOutMinX, int dstOutMinY) {
            int[] srcPixel = null;
            int[] dstPixel = null;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    srcPixel = src.getPixel(srcMinX + x, srcMinY + y, srcPixel);
                    dstPixel = dstIn.getPixel(dstInMinX + x, dstInMinY + y, dstPixel);

                    int bands = srcPixel.length;
                    int r = bands >= 4 ? srcPixel[0] : (bands > 0 ? srcPixel[0] : 0);
                    int g = bands >= 4 ? srcPixel[1] : (bands > 1 ? srcPixel[1] : 0);
                    int b = bands >= 4 ? srcPixel[2] : (bands > 2 ? srcPixel[2] : 0);
                    int sa = bands >= 4 ? srcPixel[3] : 255;

                    r = (int) (r * brightness);
                    g = (int) (g * brightness);
                    b = (int) (b * brightness);

                    if (sa < 255) {
                        int invSa = 255 - sa;
                        int dr = dstPixel.length > 0 ? dstPixel[0] : 0;
                        int dg = dstPixel.length > 1 ? dstPixel[1] : 0;
                        int db = dstPixel.length > 2 ? dstPixel[2] : 0;
                        int da = dstPixel.length >= 4 ? dstPixel[3] : 255;
                        r = (r * sa + dr * invSa) / 255;
                        g = (g * sa + dg * invSa) / 255;
                        b = (b * sa + db * invSa) / 255;
                        sa = sa + (255 - sa) * da / 255;
                    }

                    if (dstPixel.length < 4) {
                        dstPixel = new int[4];
                    }
                    dstPixel[0] = Math.min(255, Math.max(0, r));
                    dstPixel[1] = Math.min(255, Math.max(0, g));
                    dstPixel[2] = Math.min(255, Math.max(0, b));
                    dstPixel[3] = Math.min(255, Math.max(0, sa));

                    dstOut.setPixel(dstOutMinX + x, dstOutMinY + y, dstPixel);
                }
            }
        }

        @Override
        public void dispose() {
        }
    }
}
