package xyz.mcutils.backend.common.renderer;

import lombok.SneakyThrows;
import xyz.mcutils.backend.common.renderer.math.Vector3;
import xyz.mcutils.backend.model.skin.Skin;
import xyz.mcutils.backend.service.SkinService;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Comparator;
import java.util.List;

/**
 * Software 3D renderer for Minecraft player model.
 * Camera and projection match nmsr-rs exactly: look_from_yaw_pitch, orbital camera.
 */
public class Software3DRenderer {

    private static final double ASPECT_RATIO = 512.0 / 869.0;
    private static final double YAW_DEG = 45.0;   // rotates MODEL around Y (orbit effect)
    private static final double PITCH_DEG = 35.0; // rotates MODEL around X

    /** Rotate point around Y axis (yaw). Angle in degrees. */
    private static Vector3 rotateY(Vector3 v, double deg) {
        double rad = Math.toRadians(deg);
        double c = Math.cos(rad), s = Math.sin(rad);
        return new Vector3(v.getX() * c - v.getZ() * s, v.getY(), v.getX() * s + v.getZ() * c);
    }

    /** Rotate point around X axis (pitch). Angle in degrees. */
    private static Vector3 rotateX(Vector3 v, double deg) {
        double rad = Math.toRadians(deg);
        double c = Math.cos(rad), s = Math.sin(rad);
        return new Vector3(v.getX(), v.getY() * c - v.getZ() * s, v.getY() * s + v.getZ() * c);
    }

    /** Rotate vertex around center by yaw (Y) then pitch (X). */
    private static Vector3 rotAround(Vector3 v, Vector3 center, double yawDeg, double pitchDeg) {
        Vector3 t = v.subtract(center);
        Vector3 r = rotateX(rotateY(t, yawDeg), pitchDeg);
        return r.add(center);
    }

    /**
     * Project world point to view space. viewX=right, viewY=up, viewZ=depth.
     */
    private static double[] project(Vector3 world, Vector3 eye, Vector3 fwd, Vector3 right, Vector3 up) {
        double dx = world.getX() - eye.getX();
        double dy = world.getY() - eye.getY();
        double dz = world.getZ() - eye.getZ();
        double viewX = dx * right.getX() + dy * right.getY() + dz * right.getZ();
        double viewY = dx * up.getX() + dy * up.getY() + dz * up.getZ();
        double viewZ = -(dx * fwd.getX() + dy * fwd.getY() + dz * fwd.getZ());
        return new double[]{viewX, viewY, viewZ};
    }

    private static Vector3 normalize(Vector3 v) {
        double len = Math.sqrt(v.getX() * v.getX() + v.getY() * v.getY() + v.getZ() * v.getZ());
        if (len < 1e-10) return v;
        return new Vector3(v.getX() / len, v.getY() / len, v.getZ() / len);
    }

    private static Vector3 cross(Vector3 a, Vector3 b) {
        return new Vector3(
                a.getY() * b.getZ() - a.getZ() * b.getY(),
                a.getZ() * b.getX() - a.getX() * b.getZ(),
                a.getX() * b.getY() - a.getY() * b.getX()
        );
    }

    /**
     * Render the full player body. size = output height; width = size * (512/869).
     * YAW_DEG and PITCH_DEG rotate the model; camera is fixed in front.
     */
    @SneakyThrows
    public static BufferedImage render(Skin skin, boolean renderOverlays, int size) {
        int width = (int) Math.round(size * ASPECT_RATIO);
        int height = size;

        byte[] skinBytes = SkinService.INSTANCE.getSkinImage(skin);
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

        // Fixed camera: in front of model (-Z), looking at center. Model rotated by YAW/PITCH.
        Vector3 eye = new Vector3(0, 28, -45);
        Vector3 target = new Vector3(0, 16.5, 0);
        Vector3 fwd = normalize(target.subtract(eye));
        Vector3 right = normalize(cross(fwd, new Vector3(0, 1, 0)));
        Vector3 up = normalize(cross(right, fwd));

        Vector3 modelCenter = new Vector3(0, 16.5, 0);
        List<ProjectedFace> projected = new java.util.ArrayList<>();
        for (PlayerModel.Face face : faces) {
            // Rotate MODEL around center by yaw then pitch - orbit effect
            Vector3 v0 = rotAround(face.getV0(), modelCenter, YAW_DEG, PITCH_DEG);
            Vector3 v1 = rotAround(face.getV1(), modelCenter, YAW_DEG, PITCH_DEG);
            Vector3 v2 = rotAround(face.getV2(), modelCenter, YAW_DEG, PITCH_DEG);
            Vector3 v3 = rotAround(face.getV3(), modelCenter, YAW_DEG, PITCH_DEG);
            double[] p0 = project(v0, eye, fwd, right, up);
            double[] p1 = project(v1, eye, fwd, right, up);
            double[] p2 = project(v2, eye, fwd, right, up);
            double[] p3 = project(v3, eye, fwd, right, up);

            double depth = (p0[2] + p1[2] + p2[2] + p3[2]) / 4.0;
            projected.add(new ProjectedFace(
                    p0[0], p0[1], p1[0], p1[1], p2[0], p2[1], p3[0], p3[1],
                    depth,
                    face.getU0(), face.getV0_(), face.getU1(), face.getV1_()
            ));
        }

        // Sort back to front (painter's algorithm)
        projected.sort(Comparator.comparingDouble((ProjectedFace p) -> p.depth).reversed());

        // Compute scale to fit model in output
        // View space: +X right, +Y up, -Z into screen. Screen: +Y down, so flip Y when mapping.
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
        double scale = Math.min((width - 4) / modelW, (height - 4) / modelH);
        double offsetX = (width - modelW * scale) / 2 - minX * scale;
        // Screen Y: view +Y is up, screen +Y is down. Map view maxY (head) -> screen 0 (top).
        double offsetY = maxY * scale;  // so screenY = offsetY - viewY*scale puts head at top

        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

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
            if (tw <= 0 || th <= 0) continue;

            // Screen coords: X as-is; Y flipped (view +Y up -> screen top)
            double dx0 = p.x0 * scale + offsetX;
            double dy0 = offsetY - p.y0 * scale;
            double dx1 = p.x1 * scale + offsetX;
            double dy1 = offsetY - p.y1 * scale;
            double dx2 = p.x2 * scale + offsetX;
            double dy2 = offsetY - p.y2 * scale;
            double dx3 = p.x3 * scale + offsetX;
            double dy3 = offsetY - p.y3 * scale;

            BufferedImage tex = skinImage.getSubimage(sx1, sy1, tw, th);
            // Map texture (0,0)-(tw,th) to quad. v0=top_left, v1=top_right, v2=bottom_left, v3=bottom_right.
            // (0,0)->v0, (tw,0)->v1, (0,th)->v2
            double m00 = (dx1 - dx0) / tw;
            double m10 = (dy1 - dy0) / tw;
            double m01 = (dx2 - dx0) / th;
            double m11 = (dy2 - dy0) / th;
            double m02 = dx0;
            double m12 = dy0;
            AffineTransform at = new AffineTransform(m00, m10, m01, m11, m02, m12);
            g.drawImage(tex, at, null);
        }

        g.dispose();
        return result;
    }

    private record ProjectedFace(double x0, double y0, double x1, double y1,
                                 double x2, double y2, double x3, double y3,
                                 double depth,
                                 double u0, double v0_, double u1, double v1_) {}
}
