package com.nikohalfkino.rubydung.level;

/**
 * Where the camera is and which way it looks, as of the last frame that drew billboards
 * (LevelRenderer records it). It lets the selection code work out exactly where the crosshair
 * ray struck a tile, and lets the overlay turn a billboard's blink to face the camera, without
 * every caller having to pass the camera around.
 */
public final class CameraRay {

    private CameraRay() {}

    private static volatile double eyeX = Double.NaN;
    private static volatile double eyeY = Double.NaN;
    private static volatile double eyeZ = Double.NaN;
    private static volatile float yaw = Float.NaN;
    private static volatile float pitch = Float.NaN;

    /** Records the full camera. Call it from the camera setup too if the renderer only knows the angles. */
    public static void set(double x, double y, double z, float cameraYaw, float cameraPitch) {
        eyeX = x;
        eyeY = y;
        eyeZ = z;
        yaw = cameraYaw;
        pitch = cameraPitch;
    }

    /** Records just the angles (the eye position stays as it was). */
    public static void setAngles(float cameraYaw, float cameraPitch) {
        yaw = cameraYaw;
        pitch = cameraPitch;
    }

    /** True once both the eye position and the angles are known. */
    public static boolean isKnown() {
        return !Double.isNaN(eyeX) && !Float.isNaN(yaw) && !Float.isNaN(pitch);
    }

    /** Camera yaw in degrees, NaN if unknown. */
    public static float yaw() {
        return yaw;
    }

    public static double eyeX() { return eyeX; }
    public static double eyeY() { return eyeY; }
    public static double eyeZ() { return eyeZ; }

    /** Unit vector the camera looks along (same convention as the billboard depth sort). */
    public static double[] direction() {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        return new double[]{
                Math.sin(yawRad) * Math.cos(pitchRad),
                -Math.sin(pitchRad),
                -Math.cos(yawRad) * Math.cos(pitchRad)
        };
    }
}
