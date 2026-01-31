package xyz.mcutils.backend.common.renderer;

import lombok.AllArgsConstructor;
import lombok.Getter;
import xyz.mcutils.backend.common.renderer.math.Vector3;
import xyz.mcutils.backend.model.skin.Skin;

import java.util.ArrayList;
import java.util.List;

/**
 * Minecraft player model for software 3D rendering.
 * Matches nmsr-rs exactly: pos is min corner, front face at -Z (north).
 * Y=0 at feet, look_at at y=16.5.
 */
public class PlayerModel {

    /**
     * A textured face: 4 vertices (quad) with UV coordinates.
     * Vertex order: top_left, top_right, bottom_left, bottom_right (matches nmsr Quad).
     * UVs in skin texture pixels (0-64).
     */
    @AllArgsConstructor
    @Getter
    public static class Face {
        private final Vector3 v0, v1, v2, v3;  // top_left, top_right, bottom_left, bottom_right
        private final double u0, v0_, u1, v1_; // UV rect: (u0,v0_) top-left to (u1,v1_) bottom-right
    }

    /**
     * Build all faces using nmsr-rs layout exactly.
     * Part coords from nmsr minecraft.rs compute_base_part.
     */
    public static List<Face> buildFaces(Skin skin, boolean renderOverlays) {
        List<Face> faces = new ArrayList<>();
        boolean slim = skin.getModel() == Skin.Model.SLIM;
        int armW = slim ? 3 : 4;

        // Base parts - nmsr compute_base_part
        addBox(faces, -4, 24, -4, 8, 8, 8, boxUv(8, 8, 8, 8, 8));           // Head
        addBox(faces, -4, 12, -2, 8, 12, 4, boxUv(20, 20, 8, 12, 4));       // Body
        addBox(faces, slim ? -7 : -8, 12, -2, armW, 12, 4, boxUv(36, 52, armW, 12, 4));  // Left arm
        addBox(faces, 4, 12, -2, armW, 12, 4, boxUv(44, 20, armW, 12, 4));  // Right arm
        addBox(faces, -4, 0, -2, 4, 12, 4, boxUv(20, 52, 4, 12, 4));        // Left leg
        addBox(faces, 0, 0, -2, 4, 12, 4, boxUv(4, 20, 4, 12, 4));          // Right leg

        if (renderOverlays) {
            addOverlayBox(faces, -4.5, 23.5, -4.5, 9, 9, 9, boxUv(40, 8, 8, 8, 8));     // Head +32,0 expand 0.5
            addOverlayBox(faces, -4.25, 11.75, -2.25, 8.5, 12.5, 4.5, boxUv(20, 36, 8, 12, 4));  // Body +0,16 expand 0.25
            addOverlayBox(faces, slim ? -7.25 : -8.25, 11.75, -2.25, armW + 0.5, 12.5, 4.5, boxUv(52, 52, armW, 12, 4));  // Left arm +16,0
            addOverlayBox(faces, 3.75, 11.75, -2.25, armW + 0.5, 12.5, 4.5, boxUv(44, 36, armW, 12, 4));  // Right arm +0,16
            addOverlayBox(faces, -4.25, -0.25, -2.25, 4.5, 12.5, 4.5, boxUv(4, 52, 4, 12, 4));   // Left leg -16,0
            addOverlayBox(faces, -0.25, -0.25, -2.25, 4.5, 12.5, 4.5, boxUv(4, 36, 4, 12, 4));   // Right leg +0,16
        }

        return faces;
    }

    private static void addOverlayBox(List<Face> faces, double px, double py, double pz,
                                      double w, double h, double d, double[][] uvs) {
        addBox(faces, px, py, pz, w, h, d, uvs);
    }

    /**
     * nmsr uv.rs box_uv exactly: north(-Z), south(+Z), east(+X), west(-X), up(+Y), down(-Y).
     * nmsr uses down.flip_horizontally() - we apply that in addBox.
     */
    private static double[][] boxUv(int x, int y, int sizeX, int sizeY, int sizeZ) {
        return new double[][]{
                {x, y, x + sizeX, y + sizeY},                                    // north
                {x + sizeX + sizeZ, y, x + 2 * sizeX + sizeZ, y + sizeY},        // south
                {x - sizeZ, y, x, y + sizeY},                                    // east
                {x + sizeX, y, x + sizeX + sizeZ, y + sizeY},                    // west
                {x, y - sizeZ, x + sizeX, y},                                    // up
                {x + sizeX, y - sizeZ, x + 2 * sizeX, y}                         // down (flip_h applied below)
        };
    }

    /**
     * Add box with nmsr cube layout. pos = min corner.
     * Front = -Z (north), Back = +Z (south).
     */
    private static void addBox(List<Face> faces, double px, double py, double pz,
                               double w, double h, double d, double[][] uvs) {
        double x0 = px, x1 = px + w;
        double y0 = py, y1 = py + h;
        double z0 = pz, z1 = pz + d;

        // Front (north, -Z): use south UVs - Minecraft skin has front/back swapped vs nmsr box_uv
        faces.add(new Face(
                new Vector3(x1, y1, z0), new Vector3(x0, y1, z0),
                new Vector3(x1, y0, z0), new Vector3(x0, y0, z0),
                uvs[1][0], uvs[1][1], uvs[1][2], uvs[1][3]));

        // Back (south, +Z): use north UVs
        faces.add(new Face(
                new Vector3(x1, y1, z1), new Vector3(x0, y1, z1),
                new Vector3(x1, y0, z1), new Vector3(x0, y0, z1),
                uvs[0][0], uvs[0][1], uvs[0][2], uvs[0][3]));

        // Top (up, +Y): at y=y1
        faces.add(new Face(
                new Vector3(x1, y1, z0), new Vector3(x0, y1, z0),
                new Vector3(x1, y1, z1), new Vector3(x0, y1, z1),
                uvs[4][0], uvs[4][1], uvs[4][2], uvs[4][3]));

        // Bottom (down, -Y): nmsr uses down.flip_horizontally() - swap u0/u1
        faces.add(new Face(
                new Vector3(x0, y0, z0), new Vector3(x1, y0, z0),
                new Vector3(x0, y0, z1), new Vector3(x1, y0, z1),
                uvs[5][2], uvs[5][1], uvs[5][0], uvs[5][3]));  // flip_h: swap u0,u1

        // Left (west, -X): at x=x0
        faces.add(new Face(
                new Vector3(x0, y1, z1), new Vector3(x0, y1, z0),
                new Vector3(x0, y0, z1), new Vector3(x0, y0, z0),
                uvs[3][0], uvs[3][1], uvs[3][2], uvs[3][3]));

        // Right (east, +X): at x=x1
        faces.add(new Face(
                new Vector3(x1, y1, z0), new Vector3(x1, y1, z1),
                new Vector3(x1, y0, z0), new Vector3(x1, y0, z1),
                uvs[2][0], uvs[2][1], uvs[2][2], uvs[2][3]));
    }
}
