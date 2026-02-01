package xyz.mcutils.backend.common.renderer.impl.skin;

import lombok.SneakyThrows;
import xyz.mcutils.backend.common.math.Vector3;
import xyz.mcutils.backend.common.math.Vector3Utils;
import xyz.mcutils.backend.common.renderer.PlayerModel;
import xyz.mcutils.backend.common.renderer.SkinRenderer;
import xyz.mcutils.backend.model.skin.ISkinPart;
import xyz.mcutils.backend.model.skin.Skin;
import xyz.mcutils.backend.service.SkinService;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Renders a full Minecraft player body using software 3D rendering.
 * Uses orthographic projection with a fixed camera; the model is rotated by
 * {@link #YAW_DEG} and {@link #PITCH_DEG} to produce an isometric view.
 */
public class IsometricFullBodyRenderer extends SkinRenderer<ISkinPart.Custom> {
    public static final IsometricFullBodyRenderer INSTANCE = new IsometricFullBodyRenderer();

    private static final double ASPECT_RATIO = 512.0 / 869.0;
    private static final double YAW_DEG = 45.0;
    private static final double PITCH_DEG = 35.0;

    @Override
    @SneakyThrows
    public BufferedImage render(Skin skin, ISkinPart.Custom part, boolean renderOverlays, int size) {
        int width = (int) Math.round(size * ASPECT_RATIO);

        // Load and normalize skin to 64x64
        byte[] skinBytes = SkinService.INSTANCE.getSkinImage(skin, true);
        BufferedImage skinImage = ImageIO.read(new ByteArrayInputStream(skinBytes));
        if (skinImage == null) {
            throw new IllegalStateException("Failed to load skin image");
        }

        if (skinImage.getWidth() != 64 || skinImage.getHeight() != 64) {
            BufferedImage normalized = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = normalized.createGraphics();
            g.drawImage(skinImage, 0, 0, 64, 64, null);
            g.dispose();
            skinImage = normalized;
        }

        List<PlayerModel.Face> faces = PlayerModel.buildFaces(skin, renderOverlays);

        // Camera at fixed position, looking at model center
        Vector3 eye = new Vector3(0, 28, -45);
        Vector3 target = new Vector3(0, 16.5, 0);
        Vector3 fwd = Vector3Utils.normalize(target.subtract(eye));
        Vector3 right = Vector3Utils.normalize(Vector3Utils.cross(fwd, new Vector3(0, 1, 0)));
        Vector3 up = Vector3Utils.normalize(Vector3Utils.cross(right, fwd));

        Vector3 modelCenter = new Vector3(0, 16.5, 0);
        // Rotate model by yaw/pitch, then project each face to 2D
        List<ProjectedFace> projected = new ArrayList<>();
        for (PlayerModel.Face face : faces) {
            Vector3 v0 = Vector3Utils.rotAround(face.getV0(), modelCenter, YAW_DEG, PITCH_DEG);
            Vector3 v1 = Vector3Utils.rotAround(face.getV1(), modelCenter, YAW_DEG, PITCH_DEG);
            Vector3 v2 = Vector3Utils.rotAround(face.getV2(), modelCenter, YAW_DEG, PITCH_DEG);
            Vector3 v3 = Vector3Utils.rotAround(face.getV3(), modelCenter, YAW_DEG, PITCH_DEG);
            double[] p0 = Vector3Utils.project(v0, eye, fwd, right, up);
            double[] p1 = Vector3Utils.project(v1, eye, fwd, right, up);
            double[] p2 = Vector3Utils.project(v2, eye, fwd, right, up);
            double[] p3 = Vector3Utils.project(v3, eye, fwd, right, up);

            double depth = (p0[2] + p1[2] + p2[2] + p3[2]) / 4.0;
            projected.add(new ProjectedFace(
                    p0[0], p0[1], p1[0], p1[1], p2[0], p2[1], p3[0], p3[1],
                    depth,
                    face.getU0(), face.getV0_(), face.getU1(), face.getV1_()
            ));
        }

        projected.sort(Comparator.comparingDouble((ProjectedFace p) -> p.depth).reversed());

        // Compute bounding box and scale to fit output with padding
        double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE;
        double minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;
        for (ProjectedFace p : projected) {
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

        // Draw each face: sample texture region and map to screen quad via AffineTransform
        for (ProjectedFace p : projected) {
            int sx1 = (int) Math.floor(p.u0);
            int sy1 = (int) Math.floor(p.v0_);
            int sx2 = (int) Math.ceil(p.u1);
            int sy2 = (int) Math.ceil(p.v1_);
            sx1 = Math.max(0, Math.min(sx1, 63));
            sy1 = Math.max(0, Math.min(sy1, 63));
            sx2 = Math.max(sx1 + 1, Math.min(sx2, 64));
            sy2 = Math.max(sy1 + 1, Math.min(sy2, 64));
            int tw = sx2 - sx1;
            int th = sy2 - sy1;
            if (tw <= 0 || th <= 0) continue; // Skip degenerate UV regions

            double dx0 = p.x0 * scale + offsetX;
            double dy0 = offsetY - p.y0 * scale;
            double dx1 = p.x1 * scale + offsetX;
            double dy1 = offsetY - p.y1 * scale;
            double dx2 = p.x2 * scale + offsetX;
            double dy2 = offsetY - p.y2 * scale;

            BufferedImage tex = skinImage.getSubimage(sx1, sy1, tw, th);
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

    /**
     * A face after projection to 2D, with screen coordinates and UV bounds.
     */
    private record ProjectedFace(double x0, double y0, double x1, double y1,
                                 double x2, double y2, double x3, double y3,
                                 double depth,
                                 double u0, double v0_, double u1, double v1_) {}
}
