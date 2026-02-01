package xyz.mcutils.backend.common.renderer.impl.software;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * Fast software rasterizer for textured quads. Writes directly to pixel buffer
 * with nearest-neighbor sampling and optional brightness. Avoids Graphics2D overhead.
 */
public final class QuadRasterizer {

    private QuadRasterizer() {}

    /**
     * Rasterize a screen-space quad with texture mapping and brightness.
     * Uses direct quad scanline (no triangle split) to avoid seam artifacts
     * along the diagonal. Quad vertices: v0 top-left, v1 top-right, v2 bottom-left, v3 bottom-right.
     * UV: (u0,v0) at v0, (u1,v0) at v1, (u0,v1) at v2, (u1,v1) at v3.
     */
    public static void rasterizeQuad(
            int[] outPixels, int outW, int outH,
            double sx0, double sy0, double sx1, double sy1, double sx2, double sy2, double sx3, double sy3,
            double u0, double v0, double u1, double v1,
            int[] texPixels, int texW, int texH,
            float brightness) {

        // Pixel center (i+0.5, j+0.5) must be inside quad: j from ceil(minY-0.5) to floor(maxY-0.5)
        double quadMinY = Math.min(Math.min(sy0, sy1), Math.min(sy2, sy3));
        double quadMaxY = Math.max(Math.max(sy0, sy1), Math.max(sy2, sy3));
        int yMin = (int) Math.ceil(quadMinY - 0.5);
        int yMax = (int) Math.floor(quadMaxY - 0.5);
        yMin = Math.max(0, yMin);
        yMax = Math.min(outH - 1, yMax);

        if (yMin > yMax) return;

        // Edges: 0-1 (top), 1-3 (right), 3-2 (bottom), 2-0 (left)
        double[] ex = {sx0, sx1, sx3, sx2};
        double[] ey = {sy0, sy1, sy3, sy2};
        double[] eu = {u0, u1, u1, u0};
        double[] ev = {v0, v0, v1, v1};

        for (int y = yMin; y <= yMax; y++) {
            double py = y + 0.5;
            double xMin = Double.POSITIVE_INFINITY;
            double xMax = Double.NEGATIVE_INFINITY;
            double uAtLeft = 0, vAtLeft = 0, uAtRight = 0, vAtRight = 0;

            // Epsilon so boundary scanlines aren't skipped by floating point
            final double eps = 1e-6;
            for (int e = 0; e < 4; e++) {
                int e1 = (e + 1) % 4;
                double yA = ey[e], yB = ey[e1];
                if (Math.abs(yB - yA) < 1e-9) continue;
                if (py < Math.min(yA, yB) - eps || py > Math.max(yA, yB) + eps) continue;

                double t = (py - yA) / (yB - yA);
                double x = ex[e] + t * (ex[e1] - ex[e]);
                double u = eu[e] + t * (eu[e1] - eu[e]);
                double v = ev[e] + t * (ev[e1] - ev[e]);

                if (x < xMin) {
                    xMin = x;
                    uAtLeft = u;
                    vAtLeft = v;
                }
                if (x > xMax) {
                    xMax = x;
                    uAtRight = u;
                    vAtRight = v;
                }
            }

            if (xMin > xMax) continue;

            // Pixel (i,j) is drawn iff its center (i+0.5, j+0.5) is inside the quad.
            // So first x: ceil(xMin - 0.5), last x: floor(xMax - 0.5)
            int xStart = Math.max(0, (int) Math.ceil(xMin - 0.5));
            int xEnd = Math.min(outW - 1, (int) Math.floor(xMax - 0.5));
            if (xStart > xEnd) {
                int xMid = (int) Math.round((xMin + xMax) / 2);
                if (xMid >= 0 && xMid < outW) {
                    xStart = xMid;
                    xEnd = xMid;
                } else continue;
            }

            double dx = xMax - xMin;
            double du = (uAtRight - uAtLeft) / (dx > 1e-9 ? dx : 1);
            double dv = (vAtRight - vAtLeft) / (dx > 1e-9 ? dx : 1);

            double u = uAtLeft + (xStart - xMin + 0.5) * du;
            double v = vAtLeft + (xStart - xMin + 0.5) * dv;

            int rowOffset = y * outW;
            for (int x = xStart; x <= xEnd; x++) {
                int texX = (int) Math.floor(u);
                int texY = (int) Math.floor(v);
                texX = Math.max(0, Math.min(texX, texW - 1));
                texY = Math.max(0, Math.min(texY, texH - 1));

                int pixel = texPixels[texY * texW + texX];
                int a = (pixel >> 24) & 0xFF;
                if (a == 0) {
                    u += du;
                    v += dv;
                    continue;
                }

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
                if (a >= 254) {
                    outPixels[dstIdx] = (a << 24) | (r << 16) | (g << 8) | b;
                } else {
                    int da = (dst >> 24) & 0xFF;
                    int dr = (dst >> 16) & 0xFF;
                    int dg = (dst >> 8) & 0xFF;
                    int db = dst & 0xFF;
                    int invSa = 255 - a;
                    r = (r * a + dr * invSa) / 255;
                    g = (g * a + dg * invSa) / 255;
                    b = (b * a + db * invSa) / 255;
                    a = a + (255 - a) * da / 255;
                    outPixels[dstIdx] = (Math.min(255, a) << 24) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
                }

                u += du;
                v += dv;
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
