package xyz.mcutils.backend.common.renderer.math;

/**
 * 4x4 matrix for view/projection in software 3D rendering.
 * Row-major layout: m[row][col] -> mXY where X=row, Y=col
 */
public class Matrix4 {
    public double m00, m01, m02, m03;
    public double m10, m11, m12, m13;
    public double m20, m21, m22, m23;
    public double m30, m31, m32, m33;

    public Matrix4() {
        setIdentity();
    }

    public void setIdentity() {
        m00 = 1; m01 = 0; m02 = 0; m03 = 0;
        m10 = 0; m11 = 1; m12 = 0; m13 = 0;
        m20 = 0; m21 = 0; m22 = 1; m23 = 0;
        m30 = 0; m31 = 0; m32 = 0; m33 = 1;
    }

    /** Rotation 180 deg around Y: (x,y,z) -> (-x,y,-z). Swaps front/back. */
    public static Matrix4 rotationY180() {
        Matrix4 m = new Matrix4();
        m.m00 = -1; m.m11 = 1; m.m22 = -1;
        m.m30 = 0; m.m31 = 0; m.m32 = 0;
        return m;
    }

    /**
     * Create view matrix for orbital camera around look_at.
     * Yaw 45° + pitch 35.264° from front-right-above, seeing the player's face (-Z).
     * Camera on sphere: azimuth=yaw (XZ plane), elevation=pitch (angle up from XZ).
     */
    public static Matrix4 viewMatrix(double yawDeg, double pitchDeg, double distance, double lookAtX, double lookAtY, double lookAtZ) {
        // nmsr look_from_yaw_pitch: (-yaw)-PI, (-pitch), FLIP_X_AND_Z (-1,1,-1)
        double yawRad = Math.toRadians(-yawDeg) - Math.PI;
        double pitchRad = Math.toRadians(-pitchDeg);
        // Spherical: cam at look_at + distance * (cos(pitch)*sin(yaw), sin(pitch), cos(pitch)*cos(yaw))
        // yaw=135° places cam in front-right; pitch=35° places cam above
        double lx = Math.sin(yawRad) * Math.cos(pitchRad) * -1;
        double ly = Math.sin(pitchRad);
        double lz = Math.cos(yawRad) * Math.cos(pitchRad) * -1;
        Vector3 eye = new Vector3(
                lookAtX - lx * distance,
                lookAtY - ly * distance,
                lookAtZ - lz * distance
        );
        Vector3 target = new Vector3(lookAtX, lookAtY, lookAtZ);
        Vector3 up = new Vector3(0, 1, 0);

        Vector3 f = target.subtract(eye);
        double len = Math.sqrt(f.getX() * f.getX() + f.getY() * f.getY() + f.getZ() * f.getZ());
        f = new Vector3(f.getX() / len, f.getY() / len, f.getZ() / len);

        Vector3 u = new Vector3(up.getX(), up.getY(), up.getZ());
        double dot = f.getX() * u.getX() + f.getY() * u.getY() + f.getZ() * u.getZ();
        u = new Vector3(u.getX() - dot * f.getX(), u.getY() - dot * f.getY(), u.getZ() - dot * f.getZ());
        len = Math.sqrt(u.getX() * u.getX() + u.getY() * u.getY() + u.getZ() * u.getZ());
        u = new Vector3(u.getX() / len, u.getY() / len, u.getZ() / len);

        Vector3 s = new Vector3(
                f.getY() * u.getZ() - f.getZ() * u.getY(),
                f.getZ() * u.getX() - f.getX() * u.getZ(),
                f.getX() * u.getY() - f.getY() * u.getX()
        );

        // View matrix V: viewPos = V * worldPos. Vector3.transform uses columns for output:
        // result.x = m00*x + m10*y + m20*z + m30, so col0 = (m00,m10,m20,m30)
        // Need col0=(s.x,s.y,s.z,-s·eye), col1=(u.x,u.y,u.z,-u·eye), col2=(-f.x,-f.y,-f.z,f·eye)
        double sDotE = s.getX() * eye.getX() + s.getY() * eye.getY() + s.getZ() * eye.getZ();
        double uDotE = u.getX() * eye.getX() + u.getY() * eye.getY() + u.getZ() * eye.getZ();
        double fDotE = f.getX() * eye.getX() + f.getY() * eye.getY() + f.getZ() * eye.getZ();
        Matrix4 m = new Matrix4();
        m.m00 = s.getX(); m.m10 = s.getY(); m.m20 = s.getZ(); m.m30 = -sDotE;
        m.m01 = u.getX(); m.m11 = u.getY(); m.m21 = u.getZ(); m.m31 = -uDotE;
        m.m02 = -f.getX(); m.m12 = -f.getY(); m.m22 = -f.getZ(); m.m32 = fDotE;
        m.m03 = 0; m.m13 = 0; m.m23 = 0; m.m33 = 1;

        return m;
    }

    /**
     * Orthographic projection: map 3D view space to 2D with given scale.
     * Returns (screenX, screenY) from view-space (x, y, z).
     * We use x,y directly scaled; z for depth sorting.
     */
    public static double[] orthographicProject(double viewX, double viewY, double viewZ,
                                               double scale, double centerX, double centerY) {
        return new double[]{
                centerX + viewX * scale,
                centerY - viewY * scale, // Flip Y for screen coords
                viewZ // Keep for depth
        };
    }
}
