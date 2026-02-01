package xyz.mcutils.backend.common.renderer.impl.software;

import lombok.extern.slf4j.Slf4j;
import xyz.mcutils.backend.common.math.Vector3;
import xyz.mcutils.backend.common.math.Vector3Utils;
import xyz.mcutils.backend.common.renderer.IsometricLighting;
import xyz.mcutils.backend.common.renderer.Isometric3DRenderer;
import xyz.mcutils.backend.common.renderer.model.Face;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Software (CPU/Graphics2D) implementation of the 3D isometric renderer.
 * Rotates by yaw/pitch, orthographically projects, depth-sorts, and draws quads.
 * Uses IsometricLighting for sun-based lighting (same as GPU path).
 */
@Slf4j
public class SoftwareIsometric3DRenderer implements Isometric3DRenderer {

    @Override
    public BufferedImage render(List<TexturedFaces> batches, ViewParams view, int size) {
        long t0 = System.nanoTime();
        int width = (int) Math.round(size * view.aspectRatio());

        Vector3 eye = view.eye();
        Vector3 target = view.target();
        Vector3 fwd = Vector3Utils.normalize(target.subtract(eye));
        Vector3 right = Vector3Utils.normalize(Vector3Utils.cross(fwd, new Vector3(0, 1, 0)));
        Vector3 up = Vector3Utils.normalize(Vector3Utils.cross(right, fwd));

        double yaw = view.yawDeg();
        double pitch = view.pitchDeg();

        int totalFaces = batches.stream().mapToInt(b -> b.faces().size()).sum();
        List<ProjectedFaceWithTexture> projected = new ArrayList<>(totalFaces);

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

        int faceIndex = 0;
        for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
            TexturedFaces batch = batches.get(batchIndex);
            BufferedImage texture = batch.texture();
            int texW = texture.getWidth();
            int texH = texture.getHeight();

            for (Face face : batch.faces()) {
                var rotatedNormal = Vector3Utils.rotateX(Vector3Utils.rotateY(face.normal(), yaw), pitch);
                double brightness = IsometricLighting.computeBrightness(
                        rotatedNormal, IsometricLighting.SUN_DIRECTION, IsometricLighting.MIN_BRIGHTNESS);

                Vector3 v0 = Vector3Utils.rotAround(face.v0(), target, yaw, pitch);
                Vector3 v1 = Vector3Utils.rotAround(face.v1(), target, yaw, pitch);
                Vector3 v2 = Vector3Utils.rotAround(face.v2(), target, yaw, pitch);
                Vector3 v3 = Vector3Utils.rotAround(face.v3(), target, yaw, pitch);
                double[] p0 = Vector3Utils.project(v0, eye, fwd, right, up);
                double[] p1 = Vector3Utils.project(v1, eye, fwd, right, up);
                double[] p2 = Vector3Utils.project(v2, eye, fwd, right, up);
                double[] p3 = Vector3Utils.project(v3, eye, fwd, right, up);

                double depth = (p0[2] + p1[2] + p2[2] + p3[2]) / 4.0;

                double x0 = p0[0], y0 = p0[1], x1 = p1[0], y1 = p1[1], x2 = p2[0], y2 = p2[1], x3 = p3[0], y3 = p3[1];
                projected.add(new ProjectedFaceWithTexture(
                        x0, y0, x1, y1, x2, y2, x3, y3,
                        depth,
                        face.u0(), face.v0_(), face.u1(), face.v1_(),
                        brightness,
                        batchIndex,
                        texW, texH,
                        faceIndex++
                ));
                minX = Math.min(minX, Math.min(Math.min(x0, x1), Math.min(x2, x3)));
                maxX = Math.max(maxX, Math.max(Math.max(x0, x1), Math.max(x2, x3)));
                minY = Math.min(minY, Math.min(Math.min(y0, y1), Math.min(y2, y3)));
                maxY = Math.max(maxY, Math.max(Math.max(y0, y1), Math.max(y2, y3)));
            }
        }
        double tProject = (System.nanoTime() - t0) / 1e6;

        long tSort = System.nanoTime();
        // Stable sort: depth back-to-front, then by face index so coplanar faces (e.g. head/body seam) draw consistently
        projected.sort(Comparator.comparingDouble((ProjectedFaceWithTexture p) -> p.depth).reversed()
                .thenComparingInt(p -> p.faceIndex));
        double tSortMs = (System.nanoTime() - tSort) / 1e6;
        double modelW = maxX - minX;
        double modelH = maxY - minY;
        if (modelW < 1) modelW = 1;
        if (modelH < 1) modelH = 1;
        double scale = Math.min((width - 4) / modelW, (size - 4) / modelH);
        double offsetX = (width - modelW * scale) / 2 - minX * scale;
        double offsetY = maxY * scale;

        BufferedImage result = new BufferedImage(width, size, BufferedImage.TYPE_INT_ARGB);
        int[] outPixels = ((DataBufferInt) result.getRaster().getDataBuffer()).getData();

        // Pre-load texture pixels per batch (avoids repeated getRGB in loop)
        int[][] batchTexPixels = new int[batches.size()][];
        for (int i = 0; i < batches.size(); i++) {
            batchTexPixels[i] = QuadRasterizer.getTexturePixels(batches.get(i).texture());
        }

        long tDraw = System.nanoTime();
        for (ProjectedFaceWithTexture p : projected) {
            int[] texPixels = batchTexPixels[p.textureIndex];
            int texW = p.texW;
            int texH = p.texH;

            double dx0 = p.x0 * scale + offsetX;
            double dy0 = offsetY - p.y0 * scale;
            double dx1 = p.x1 * scale + offsetX;
            double dy1 = offsetY - p.y1 * scale;
            double dx2 = p.x2 * scale + offsetX;
            double dy2 = offsetY - p.y2 * scale;
            double dx3 = p.x3 * scale + offsetX;
            double dy3 = offsetY - p.y3 * scale;

            QuadRasterizer.rasterizeQuad(
                    outPixels, width, size,
                    dx0, dy0, dx1, dy1, dx2, dy2, dx3, dy3,
                    p.u0, p.v0_, p.u1, p.v1_,
                    texPixels, texW, texH,
                    (float) p.brightness());
        }
        double tDrawMs = (System.nanoTime() - tDraw) / 1e6;

        if (log.isDebugEnabled()) {
            log.debug("Software render profile: project={}ms sort={}ms draw={}ms",
                    String.format("%.2f", tProject), String.format("%.2f", tSortMs), String.format("%.2f", tDrawMs));
        }
        return result;
    }

    private record ProjectedFaceWithTexture(double x0, double y0, double x1, double y1,
                                            double x2, double y2, double x3, double y3,
                                            double depth,
                                            double u0, double v0_, double u1, double v1_,
                                            double brightness,
                                            int textureIndex,
                                            int texW, int texH,
                                            int faceIndex) {}
}
