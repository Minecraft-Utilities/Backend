package xyz.mcutils.backend.common.math;

/**
 * Static utilities for 3D vector operations (rotation, projection, normalize, cross).
 */
public final class Vector3Utils {
    /**
     * Rotates a point around the Y axis (yaw).
     *
     * @param v   the point to rotate
     * @param deg angle in degrees
     * @return the rotated point
     */
    public static Vector3 rotateY(Vector3 v, double deg) {
        double rad = Math.toRadians(deg);
        double c = Math.cos(rad), s = Math.sin(rad);
        return new Vector3(v.getX() * c - v.getZ() * s, v.getY(), v.getX() * s + v.getZ() * c);
    }

    /**
     * Rotates a point around the X axis (pitch).
     *
     * @param v   the point to rotate
     * @param deg angle in degrees
     * @return the rotated point
     */
    public static Vector3 rotateX(Vector3 v, double deg) {
        double rad = Math.toRadians(deg);
        double c = Math.cos(rad), s = Math.sin(rad);
        return new Vector3(v.getX(), v.getY() * c - v.getZ() * s, v.getY() * s + v.getZ() * c);
    }

    /**
     * Rotates a vertex around a center point by yaw (Y axis) then pitch (X axis).
     *
     * @param v         the vertex to rotate
     * @param center    the rotation center
     * @param yawDeg    yaw angle in degrees
     * @param pitchDeg  pitch angle in degrees
     * @return the rotated vertex
     */
    public static Vector3 rotAround(Vector3 v, Vector3 center, double yawDeg, double pitchDeg) {
        Vector3 t = v.subtract(center);
        Vector3 r = rotateX(rotateY(t, yawDeg), pitchDeg);
        return r.add(center);
    }

    /**
     * Orthographically projects a world-space point into view space.
     *
     * @param world the world-space point
     * @param eye   the camera position
     * @param fwd   the camera forward direction
     * @param right the camera right direction
     * @param up    the camera up direction
     * @return [viewX, viewY, viewZ] where viewX is right, viewY is up, viewZ is depth
     */
    public static double[] project(Vector3 world, Vector3 eye, Vector3 fwd, Vector3 right, Vector3 up) {
        double dx = world.getX() - eye.getX();
        double dy = world.getY() - eye.getY();
        double dz = world.getZ() - eye.getZ();
        double viewX = dx * right.getX() + dy * right.getY() + dz * right.getZ();
        double viewY = dx * up.getX() + dy * up.getY() + dz * up.getZ();
        double viewZ = -(dx * fwd.getX() + dy * fwd.getY() + dz * fwd.getZ());
        return new double[]{viewX, viewY, viewZ};
    }

    /** Normalizes a vector to unit length. */
    public static Vector3 normalize(Vector3 v) {
        double len = Math.sqrt(v.getX() * v.getX() + v.getY() * v.getY() + v.getZ() * v.getZ());
        if (len < 1e-10) return v;
        return new Vector3(v.getX() / len, v.getY() / len, v.getZ() / len);
    }

    /** Cross product of two vectors. */
    public static Vector3 cross(Vector3 a, Vector3 b) {
        return new Vector3(
                a.getY() * b.getZ() - a.getZ() * b.getY(),
                a.getZ() * b.getX() - a.getX() * b.getZ(),
                a.getX() * b.getY() - a.getY() * b.getX()
        );
    }
}
