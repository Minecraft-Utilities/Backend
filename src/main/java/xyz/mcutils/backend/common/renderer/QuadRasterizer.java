package xyz.mcutils.backend.common.renderer;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * Fast software rasterizer for textured quads. Writes directly to pixel buffer
 * with nearest-neighbor sampling and optional brightness. Matches Graphics2D.drawImage( AffineTransform )
 * by iterating destination pixels, inverse-mapping to texture, and sampling (destination-driven).
 */
public final class QuadRasterizer {
    /**
     * Rasterize a parallelogram like Graphics2D.drawImage( subimage, AffineTransform ).
     * Parallelogram: (dx0,dy0)=texture(texX0,texY0), (dx1,dy1)=texture(texX0+tw,texY0), (dx2,dy2)=texture(texX0,texY0+th).
     * For each screen pixel in the parallelogram bbox, inverse-map to (u',v') in [0,1]^2, sample texture, write.
     */
    public static void rasterizeQuad(
            int[] outPixels, int outW, int outH,
            double dx0, double dy0, double dx1, double dy1, double dx2, double dy2,
            int texX0, int texY0, int tw, int th,
            int[] texPixels, int texW, int texH,
            float brightness) {

        if (tw <= 0 || th <= 0) return;

        double dx3 = dx1 + dx2 - dx0;
        double dy3 = dy1 + dy2 - dy0;
        int xMin = (int) Math.floor(Math.min(Math.min(dx0, dx1), Math.min(dx2, dx3)));
        int xMax = (int) Math.ceil(Math.max(Math.max(dx0, dx1), Math.max(dx2, dx3)));
        int yMin = (int) Math.floor(Math.min(Math.min(dy0, dy1), Math.min(dy2, dy3)));
        int yMax = (int) Math.ceil(Math.max(Math.max(dy0, dy1), Math.max(dy2, dy3)));
        xMin = Math.max(0, xMin);
        xMax = Math.min(outW - 1, xMax);
        yMin = Math.max(0, yMin);
        yMax = Math.min(outH - 1, yMax);
        if (xMin > xMax || yMin > yMax) return;

        // Inverse: (x-dx0, y-dy0) = u'*(dx1-dx0, dy1-dy0) + v'*(dx2-dx0, dy2-dy0) => solve for (u', v')
        double ax = dx1 - dx0, ay = dy1 - dy0, cx = dx2 - dx0, cy = dy2 - dy0;
        double det = ax * cy - ay * cx;
        if (Math.abs(det) < 1e-12) return;

        for (int y = yMin; y <= yMax; y++) {
            double py = y + 0.5;
            int rowOffset = y * outW;
            for (int x = xMin; x <= xMax; x++) {
                double px = x + 0.5;
                double rx = px - dx0, ry = py - dy0;
                double u = (rx * cy - ry * cx) / det;
                double v = (ry * ax - rx * ay) / det;
                if (u < 0 || u > 1 || v < 0 || v > 1) continue;

                int texX = texX0 + (int) Math.floor(u * tw);
                int texY = texY0 + (int) Math.floor(v * th);
                texX = Math.max(0, Math.min(texX, texW - 1));
                texY = Math.max(0, Math.min(texY, texH - 1));

                int pixel = texPixels[texY * texW + texX];
                int a_ = (pixel >> 24) & 0xFF;
                if (a_ == 0) continue;

                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                if (brightness != 1.0f) {
                    r = (int) (r * brightness);
                    g = (int) (g * brightness);
                    b = (int) (b * brightness);
                    r = Math.min(255, Math.max(0, r));
                    g = Math.min(255, Math.max(0, g));
                    b = Math.min(255, Math.max(0, b));
                }

                int dstIdx = rowOffset + x;
                int dst = outPixels[dstIdx];
                if (a_ >= 254) {
                    outPixels[dstIdx] = (a_ << 24) | (r << 16) | (g << 8) | b;
                } else {
                    int da = (dst >> 24) & 0xFF;
                    int dr = (dst >> 16) & 0xFF;
                    int dg = (dst >> 8) & 0xFF;
                    int db = dst & 0xFF;
                    int invSa = 255 - a_;
                    r = (r * a_ + dr * invSa) / 255;
                    g = (g * a_ + dg * invSa) / 255;
                    b = (b * a_ + db * invSa) / 255;
                    a_ = a_ + (255 - a_) * da / 255;
                    outPixels[dstIdx] = (Math.min(255, a_) << 24) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
                }
            }
        }
    }

    /**
     * Get texture pixels as int[]. Uses DataBufferInt when available for zero-copy.
     */
    public static int[] getTexturePixels(BufferedImage texture) {
        if (texture.getRaster().getDataBuffer() instanceof DataBufferInt db) {
            return db.getData();
        }
        // Fallback: copy via getRGB
        int w = texture.getWidth();
        int h = texture.getHeight();
        return texture.getRGB(0, 0, w, h, null, 0, w);
    }

}
