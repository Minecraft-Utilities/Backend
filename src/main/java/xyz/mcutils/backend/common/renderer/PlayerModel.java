package xyz.mcutils.backend.common.renderer;

import lombok.AllArgsConstructor;
import lombok.Getter;
import xyz.mcutils.backend.common.renderer.math.Vector3;
import xyz.mcutils.backend.model.skin.Skin;

import java.util.ArrayList;
import java.util.List;

/**
 * Minecraft player model for software 3D rendering.
 * Coordinates: Y up, front at -Z, pos is min corner.
 */
public class PlayerModel {

    /**
     * A textured quad with 4 vertices and UV coordinates.
     */
    @AllArgsConstructor
    @Getter
    public static class Face {
        private final Vector3 v0, v1, v2, v3;
        private final double u0, v0_, u1, v1_;
    }

    /**
     * Builds all faces for the player model.
     *
     * @param skin           the skin (determines slim/classic arms)
     * @param renderOverlays whether to include the overlay layer
     * @return the list of textured faces
     */
    public static List<Face> buildFaces(Skin skin, boolean renderOverlays) {
        List<Face> faces = new ArrayList<>();
        boolean slim = skin.getModel() == Skin.Model.SLIM;
        int armW = slim ? 3 : 4;

        // Base layer: head, body, left arm, right arm, left leg, right leg
        addBox(faces, -4, 24, -4, 8, 8, 8, boxUv(8, 8, 8, 8, 8));
        addBox(faces, -4, 12, -2, 8, 12, 4, boxUv(20, 20, 8, 12, 4));
        addBox(faces, slim ? -7 : -8, 12, -2, armW, 12, 4, boxUv(36, 52, armW, 12, 4));
        addBox(faces, 4, 12, -2, armW, 12, 4, boxUv(44, 20, armW, 12, 4));
        addBox(faces, -4, 0, -2, 4, 12, 4, boxUv(20, 52, 4, 12, 4));
        addBox(faces, 0, 0, -2, 4, 12, 4, boxUv(4, 20, 4, 12, 4));

        if (renderOverlays) {
            // Overlay layer: slightly larger boxes with second-layer UVs
            addBox(faces, -4.5, 23.5, -4.5, 9, 9, 9, boxUv(40, 8, 8, 8, 8));
            addBox(faces, -4.25, 11.75, -2.25, 8.5, 12.5, 4.5, boxUv(20, 36, 8, 12, 4));
            addBox(faces, slim ? -7.25 : -8.25, 11.75, -2.25, armW + 0.5, 12.5, 4.5, boxUv(52, 52, armW, 12, 4));
            addBox(faces, 3.75, 11.75, -2.25, armW + 0.5, 12.5, 4.5, boxUv(44, 36, armW, 12, 4));
            addBox(faces, -4.25, -0.25, -2.25, 4.5, 12.5, 4.5, boxUv(4, 52, 4, 12, 4));
            addBox(faces, -0.25, -0.25, -2.25, 4.5, 12.5, 4.5, boxUv(4, 36, 4, 12, 4));
        }

        return faces;
    }

    /**
     * Computes UV rects for a box per face (north, south, east, west, up, down).
     */
    private static double[][] boxUv(int x, int y, int sizeX, int sizeY, int sizeZ) {
        return new double[][]{
                {x, y, x + sizeX, y + sizeY},                           // north (-Z front)
                {x + sizeX + sizeZ, y, x + 2 * sizeX + sizeZ, y + sizeY}, // south (+Z back)
                {x - sizeZ, y, x, y + sizeY},                           // east (+X)
                {x + sizeX, y, x + sizeX + sizeZ, y + sizeY},           // west (-X)
                {x, y - sizeZ, x + sizeX, y},                           // up (+Y)
                {x + sizeX, y - sizeZ, x + 2 * sizeX, y}                // down (-Y)
        };
    }

    /**
     * Adds a box (6 faces) to the face list. Front at -Z, back at +Z.
     */
    private static void addBox(List<Face> faces, double px, double py, double pz,
                               double w, double h, double d, double[][] uvs) {
        double x0 = px, x1 = px + w;
        double y0 = py, y1 = py + h;
        double z0 = pz, z1 = pz + d;

        // north (front at -Z), south (back at +Z), up, down, west (-X), east (+X)
        faces.add(new Face(
                new Vector3(x1, y1, z0), new Vector3(x0, y1, z0),
                new Vector3(x1, y0, z0), new Vector3(x0, y0, z0),
                uvs[1][0], uvs[1][1], uvs[1][2], uvs[1][3]));

        faces.add(new Face(
                new Vector3(x1, y1, z1), new Vector3(x0, y1, z1),
                new Vector3(x1, y0, z1), new Vector3(x0, y0, z1),
                uvs[0][0], uvs[0][1], uvs[0][2], uvs[0][3]));

        faces.add(new Face(
                new Vector3(x1, y1, z0), new Vector3(x0, y1, z0),
                new Vector3(x1, y1, z1), new Vector3(x0, y1, z1),
                uvs[4][0], uvs[4][1], uvs[4][2], uvs[4][3]));

        faces.add(new Face(
                new Vector3(x0, y0, z0), new Vector3(x1, y0, z0),
                new Vector3(x0, y0, z1), new Vector3(x1, y0, z1),
                uvs[5][2], uvs[5][1], uvs[5][0], uvs[5][3]));

        faces.add(new Face(
                new Vector3(x0, y1, z1), new Vector3(x0, y1, z0),
                new Vector3(x0, y0, z1), new Vector3(x0, y0, z0),
                uvs[3][0], uvs[3][1], uvs[3][2], uvs[3][3]));

        faces.add(new Face(
                new Vector3(x1, y1, z0), new Vector3(x1, y1, z1),
                new Vector3(x1, y0, z0), new Vector3(x1, y0, z1),
                uvs[2][0], uvs[2][1], uvs[2][2], uvs[2][3]));
    }
}
