package xyz.mcutils.backend.common.renderer.model;

import xyz.mcutils.backend.common.math.Vector3;
import xyz.mcutils.backend.common.math.Vector3Utils;

import java.util.List;

/**
 * Static helpers for building box meshes (UV and face generation) used by
 * PlayerModel and PlayerHeadModel.
 */
public final class ModelUtils {

    /**
     * Converts ModelBox UV array (x, y, sizeX, sizeY, sizeZ) to boxUv format.
     */
    public static double[][] uvFrom(int[] uv) {
        return boxUv(uv[0], uv[1], uv[2], uv[3], uv[4]);
    }

    /**
     * Computes UV rects for a box per face (north, south, east, west, up, down).
     * Order: north (-Z front), south (+Z back), east (+X), west (-X), up (+Y), down (-Y).
     */
    public static double[][] boxUv(int x, int y, int sizeX, int sizeY, int sizeZ) {
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
    public static void addBox(List<Face> faces, double px, double py, double pz,
                              double w, double h, double d, double[][] uvs) {
        double x0 = px, x1 = px + w;
        double y0 = py, y1 = py + h;
        double z0 = pz, z1 = pz + d;

        // north (front at -Z), south (back at +Z), up, down, west (-X), east (+X)
        faces.add(new Face(
                new Vector3(x1, y1, z0), new Vector3(x0, y1, z0),
                new Vector3(x1, y0, z0), new Vector3(x0, y0, z0),
                uvs[1][0], uvs[1][1], uvs[1][2], uvs[1][3],
                new Vector3(0, 0, -1)));

        faces.add(new Face(
                new Vector3(x1, y1, z1), new Vector3(x0, y1, z1),
                new Vector3(x1, y0, z1), new Vector3(x0, y0, z1),
                uvs[0][0], uvs[0][1], uvs[0][2], uvs[0][3],
                new Vector3(0, 0, 1)));

        faces.add(new Face(
                new Vector3(x1, y1, z0), new Vector3(x0, y1, z0),
                new Vector3(x1, y1, z1), new Vector3(x0, y1, z1),
                uvs[4][0], uvs[4][1], uvs[4][2], uvs[4][3],
                new Vector3(0, 1, 0)));

        faces.add(new Face(
                new Vector3(x0, y0, z0), new Vector3(x1, y0, z0),
                new Vector3(x0, y0, z1), new Vector3(x1, y0, z1),
                uvs[5][2], uvs[5][1], uvs[5][0], uvs[5][3],
                new Vector3(0, -1, 0)));

        faces.add(new Face(
                new Vector3(x0, y1, z1), new Vector3(x0, y1, z0),
                new Vector3(x0, y0, z1), new Vector3(x0, y0, z0),
                uvs[3][0], uvs[3][1], uvs[3][2], uvs[3][3],
                new Vector3(-1, 0, 0)));

        faces.add(new Face(
                new Vector3(x1, y1, z0), new Vector3(x1, y1, z1),
                new Vector3(x1, y0, z0), new Vector3(x1, y0, z1),
                uvs[2][0], uvs[2][1], uvs[2][2], uvs[2][3],
                new Vector3(1, 0, 0)));
    }

    /**
     * Adds a box with rotation applied to all vertices. Used for cape (nmsr-rs: rotate [5°, 180°, 0°] around anchor).
     */
    public static void addRotatedBox(List<Face> faces, double px, double py, double pz,
                                     double w, double h, double d, double[][] uvs,
                                     double anchorX, double anchorY, double anchorZ,
                                     double yawDeg, double pitchDeg) {
        Vector3 center = new Vector3(anchorX, anchorY, anchorZ);
        double x0 = px, x1 = px + w;
        double y0 = py, y1 = py + h;
        double z0 = pz, z1 = pz + d;

        Vector3 v000 = rot(new Vector3(x0, y0, z0), center, yawDeg, pitchDeg);
        Vector3 v100 = rot(new Vector3(x1, y0, z0), center, yawDeg, pitchDeg);
        Vector3 v010 = rot(new Vector3(x0, y1, z0), center, yawDeg, pitchDeg);
        Vector3 v110 = rot(new Vector3(x1, y1, z0), center, yawDeg, pitchDeg);
        Vector3 v001 = rot(new Vector3(x0, y0, z1), center, yawDeg, pitchDeg);
        Vector3 v101 = rot(new Vector3(x1, y0, z1), center, yawDeg, pitchDeg);
        Vector3 v011 = rot(new Vector3(x0, y1, z1), center, yawDeg, pitchDeg);
        Vector3 v111 = rot(new Vector3(x1, y1, z1), center, yawDeg, pitchDeg);

        Vector3 origin = new Vector3(0, 0, 0);
        Vector3 nNorth = rot(new Vector3(0, 0, -1), origin, yawDeg, pitchDeg);
        Vector3 nSouth = rot(new Vector3(0, 0, 1), origin, yawDeg, pitchDeg);
        Vector3 nUp = rot(new Vector3(0, 1, 0), origin, yawDeg, pitchDeg);
        Vector3 nDown = rot(new Vector3(0, -1, 0), origin, yawDeg, pitchDeg);
        Vector3 nWest = rot(new Vector3(-1, 0, 0), origin, yawDeg, pitchDeg);
        Vector3 nEast = rot(new Vector3(1, 0, 0), origin, yawDeg, pitchDeg);

        faces.add(new Face(v110, v010, v100, v000, uvs[1][0], uvs[1][1], uvs[1][2], uvs[1][3], nNorth));
        faces.add(new Face(v111, v011, v101, v001, uvs[0][0], uvs[0][1], uvs[0][2], uvs[0][3], nSouth));
        faces.add(new Face(v110, v111, v010, v011, uvs[4][0], uvs[4][1], uvs[4][2], uvs[4][3], nUp));
        faces.add(new Face(v000, v001, v100, v101, uvs[5][2], uvs[5][1], uvs[5][0], uvs[5][3], nDown));
        faces.add(new Face(v010, v011, v000, v001, uvs[3][0], uvs[3][1], uvs[3][2], uvs[3][3], nWest));
        faces.add(new Face(v110, v100, v111, v101, uvs[2][0], uvs[2][1], uvs[2][2], uvs[2][3], nEast));
    }

    private static Vector3 rot(Vector3 v, Vector3 center, double yawDeg, double pitchDeg) {
        return Vector3Utils.rotAround(v, center, yawDeg, pitchDeg);
    }

    /**
     * Adds a single quad (4 vertices) to the face list. Used for cape and other single-face geometry.
     */
    public static void addQuad(List<Face> faces,
                              Vector3 p0, Vector3 p1, Vector3 p2, Vector3 p3,
                              double u0, double uv0, double u1, double uv1,
                              Vector3 normal) {
        faces.add(new Face(p0, p1, p2, p3, u0, uv0, u1, uv1, normal));
    }
}
