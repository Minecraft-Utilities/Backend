package xyz.mcutils.backend.common.renderer.impl.software;

import lombok.extern.slf4j.Slf4j;
import xyz.mcutils.backend.common.math.Vector3;
import xyz.mcutils.backend.common.math.Vector3Utils;
import xyz.mcutils.backend.common.renderer.BrightnessComposite;
import xyz.mcutils.backend.common.renderer.IsometricLighting;
import xyz.mcutils.backend.common.renderer.Isometric3DRenderer;
import xyz.mcutils.backend.common.renderer.model.Face;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
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
                        texW, texH
                ));
                minX = Math.min(minX, Math.min(Math.min(x0, x1), Math.min(x2, x3)));
                maxX = Math.max(maxX, Math.max(Math.max(x0, x1), Math.max(x2, x3)));
                minY = Math.min(minY, Math.min(Math.min(y0, y1), Math.min(y2, y3)));
                maxY = Math.max(maxY, Math.max(Math.max(y0, y1), Math.max(y2, y3)));
            }
        }
        double tProject = (System.nanoTime() - t0) / 1e6;

        long tSort = System.nanoTime();
        projected.sort(Comparator.comparingDouble((ProjectedFaceWithTexture p) -> p.depth).reversed());
        double tSortMs = (System.nanoTime() - tSort) / 1e6;
        double modelW = maxX - minX;
        double modelH = maxY - minY;
        if (modelW < 1) modelW = 1;
        if (modelH < 1) modelH = 1;
        double scale = Math.min((width - 4) / modelW, (size - 4) / modelH);
        double offsetX = (width - modelW * scale) / 2 - minX * scale;
        double offsetY = maxY * scale;

        BufferedImage result = new BufferedImage(width, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        long tDraw = System.nanoTime();
        for (ProjectedFaceWithTexture p : projected) {
            BufferedImage texture = batches.get(p.textureIndex).texture();
            int texW = p.texW;
            int texH = p.texH;

            int sx1 = (int) Math.floor(p.u0);
            int sy1 = (int) Math.floor(p.v0_);
            int sx2 = (int) Math.ceil(p.u1);
            int sy2 = (int) Math.ceil(p.v1_);
            sx1 = Math.max(0, Math.min(sx1, texW - 1));
            sy1 = Math.max(0, Math.min(sy1, texH - 1));
            sx2 = Math.max(sx1 + 1, Math.min(sx2, texW));
            sy2 = Math.max(sy1 + 1, Math.min(sy2, texH));
            int tw = sx2 - sx1;
            int th = sy2 - sy1;
            if (tw <= 0 || th <= 0) continue;

            double dx0 = p.x0 * scale + offsetX;
            double dy0 = offsetY - p.y0 * scale;
            double dx1 = p.x1 * scale + offsetX;
            double dy1 = offsetY - p.y1 * scale;
            double dx2 = p.x2 * scale + offsetX;
            double dy2 = offsetY - p.y2 * scale;

            double m00 = (dx1 - dx0) / tw;
            double m10 = (dy1 - dy0) / tw;
            double m01 = (dx2 - dx0) / th;
            double m11 = (dy2 - dy0) / th;
            AffineTransform at = new AffineTransform(m00, m10, m01, m11, dx0, dy0);

            BufferedImage subimage = texture.getSubimage(sx1, sy1, tw, th);
            if (p.brightness() != 1.0) {
                Composite prevComposite = g.getComposite();
                g.setComposite(new BrightnessComposite(p.brightness()));
                g.drawImage(subimage, at, null);
                g.setComposite(prevComposite);
            } else {
                g.drawImage(subimage, at, null);
            }
        }
        double tDrawMs = (System.nanoTime() - tDraw) / 1e6;

        g.dispose();

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
                                            int texW, int texH) {}
}
