package xyz.mcutils.backend.common.renderer;

import java.awt.*;
import java.awt.image.ColorModel;
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
            int srcMinX = src.getMinX();
            int srcMinY = src.getMinY();
            int dstInMinX = dstIn.getMinX();
            int dstInMinY = dstIn.getMinY();
            int dstOutMinX = dstOut.getMinX();
            int dstOutMinY = dstOut.getMinY();

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
