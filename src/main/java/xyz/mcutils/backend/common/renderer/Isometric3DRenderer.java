package xyz.mcutils.backend.common.renderer;

import xyz.mcutils.backend.common.math.Vector3;
import xyz.mcutils.backend.common.math.Vector3Utils;
import xyz.mcutils.backend.common.renderer.model.Face;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Generic 3D isometric renderer: given textures and faces, rotates by yaw/pitch,
 * orthographically projects, sorts by depth, and draws quads. Supports multiple
 * textures (e.g. skin 64×64, cape 64×32). Used by full-body and head renderers.
 */
public class Isometric3DRenderer {

    /** Pairs a texture with its faces. Used for multi-texture rendering (skin + cape). */
    public record TexturedFaces(BufferedImage texture, List<Face> faces) {}

    /** Minimum face brightness (back/side faces); range [0, 1]. */
    private static final double MIN_BRIGHT = 0.78;

    /**
     * Renders faces with a single texture. Convenience for skin-only rendering.
     *
     * @param texture the texture (e.g. 64×64 skin)
     * @param faces   the list of textured faces
     * @param view    view parameters
     * @param size    output height in pixels
     * @return the rendered image
     */
    public static BufferedImage render(BufferedImage texture, List<Face> faces, ViewParams view, int size) {
        return render(List.of(new TexturedFaces(texture, faces)), view, size);
    }

    /**
     * Renders the given textured face batches with the given view onto an image.
     * Flattens all faces, depth-sorts, and draws using each face's associated texture.
     *
     * @param batches list of (texture, faces) — e.g. skin 64×64, cape 64×32
     * @param view    view parameters (eye, target, yaw, pitch, aspect ratio)
     * @param size    output height in pixels; width = size * aspectRatio
     * @return the rendered image
     */
    public static BufferedImage render(List<TexturedFaces> batches, ViewParams view, int size) {
        int width = (int) Math.round(size * view.aspectRatio());

        Vector3 eye = view.eye();
        Vector3 target = view.target();
        Vector3 fwd = Vector3Utils.normalize(target.subtract(eye));
        Vector3 right = Vector3Utils.normalize(Vector3Utils.cross(fwd, new Vector3(0, 1, 0)));
        Vector3 up = Vector3Utils.normalize(Vector3Utils.cross(right, fwd));

        Vector3 modelCenter = target;
        double yaw = view.yawDeg();
        double pitch = view.pitchDeg();

        List<ProjectedFaceWithTexture> projected = new ArrayList<>();
        for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
            TexturedFaces batch = batches.get(batchIndex);
            BufferedImage texture = batch.texture();
            int texW = texture.getWidth();
            int texH = texture.getHeight();

            for (Face face : batch.faces()) {
                Vector3 v0 = Vector3Utils.rotAround(face.getV0(), modelCenter, yaw, pitch);
                Vector3 v1 = Vector3Utils.rotAround(face.getV1(), modelCenter, yaw, pitch);
                Vector3 v2 = Vector3Utils.rotAround(face.getV2(), modelCenter, yaw, pitch);
                Vector3 v3 = Vector3Utils.rotAround(face.getV3(), modelCenter, yaw, pitch);
                double[] p0 = Vector3Utils.project(v0, eye, fwd, right, up);
                double[] p1 = Vector3Utils.project(v1, eye, fwd, right, up);
                double[] p2 = Vector3Utils.project(v2, eye, fwd, right, up);
                double[] p3 = Vector3Utils.project(v3, eye, fwd, right, up);

                double depth = (p0[2] + p1[2] + p2[2] + p3[2]) / 4.0;

                Vector3 rotatedNormal = Vector3Utils.normalize(
                        Vector3Utils.rotateX(Vector3Utils.rotateY(face.getNormal(), yaw), pitch));
                double dot = Vector3Utils.dot(rotatedNormal, fwd);
                double brightness = Math.max(0, Math.min(1, MIN_BRIGHT + (1.0 - MIN_BRIGHT) * (1 + dot) / 2));

                projected.add(new ProjectedFaceWithTexture(
                        p0[0], p0[1], p1[0], p1[1], p2[0], p2[1], p3[0], p3[1],
                        depth,
                        face.getU0(), face.getV0_(), face.getU1(), face.getV1_(),
                        brightness,
                        batchIndex,
                        texW, texH
                ));
            }
        }

        projected.sort(Comparator.comparingDouble((ProjectedFaceWithTexture p) -> p.depth).reversed());

        double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE;
        double minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;
        for (ProjectedFaceWithTexture p : projected) {
            minX = Math.min(minX, Math.min(Math.min(p.x0, p.x1), Math.min(p.x2, p.x3)));
            maxX = Math.max(maxX, Math.max(Math.max(p.x0, p.x1), Math.max(p.x2, p.x3)));
            minY = Math.min(minY, Math.min(Math.min(p.y0, p.y1), Math.min(p.y2, p.y3)));
            maxY = Math.max(maxY, Math.max(Math.max(p.y0, p.y1), Math.max(p.y2, p.y3)));
        }
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

            BufferedImage tex = texture.getSubimage(sx1, sy1, tw, th);
            if (p.brightness() != 1.0) {
                RescaleOp rescale = new RescaleOp(
                        new float[]{(float) p.brightness(), (float) p.brightness(), (float) p.brightness(), 1f},
                        new float[]{0f, 0f, 0f, 0f}, null);
                tex = rescale.filter(tex, null);
            }
            double m00 = (dx1 - dx0) / tw;
            double m10 = (dy1 - dy0) / tw;
            double m01 = (dx2 - dx0) / th;
            double m11 = (dy2 - dy0) / th;

            AffineTransform at = new AffineTransform(m00, m10, m01, m11, dx0, dy0);
            g.drawImage(tex, at, null);
        }

        g.dispose();
        return result;
    }

    private record ProjectedFaceWithTexture(double x0, double y0, double x1, double y1,
                                            double x2, double y2, double x3, double y3,
                                            double depth,
                                            double u0, double v0_, double u1, double v1_,
                                            double brightness,
                                            int textureIndex,
                                            int texW, int texH) {}
    
    /**
     * View parameters for the generic 3D isometric renderer.
     * The target is also used as the model rotation center.
     */
    public record ViewParams(Vector3 eye, Vector3 target, double yawDeg,
        double pitchDeg, double aspectRatio) {}
}
