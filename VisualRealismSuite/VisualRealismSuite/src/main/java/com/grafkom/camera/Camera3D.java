package com.grafkom.camera;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Kamera sintetis 3D (Synthetic Camera Model) yang terenkapsulasi penuh.
 *
 * <p>Menyimpan tiga vektor acuan: Eye (E), Target / LookAt point (At) dan Up.
 * Matriks View (LookAt) dihitung <b>manual</b> dari basis ortonormal VCS (u, v, n)
 * -- tidak memakai {@code Matrix4f.lookAt()} -- sesuai Praktikum 1.</p>
 *
 * <p>Kamera ini bertipe <i>orbital / turntable</i>: posisi Eye berputar mengelilingi
 * Target dengan koordinat sferis (radius, azimuth, elevation).</p>
 */
public class Camera3D {

    public static final float MIN_ELEVATION_DEG = -89.0f;
    public static final float MAX_ELEVATION_DEG = 89.0f;
    public static final float MIN_RADIUS = 2.0f;
    public static final float MAX_RADIUS = 40.0f;

    private final Vector3f eye;
    private final Vector3f target;
    private final Vector3f up;

    // Parameter koordinat sferis (relatif terhadap target)
    private float radius;
    private float azimuthDeg;
    private float elevationDeg;

    public Camera3D(Vector3f eye, Vector3f target, Vector3f up) {
        this.eye = new Vector3f(eye);
        this.target = new Vector3f(target);
        this.up = new Vector3f(up).normalize();
        syncSphericalFromEye();
    }

    /** Hitung radius/azimuth/elevation dari posisi eye saat ini. */
    private void syncSphericalFromEye() {
        Vector3f d = new Vector3f();
        eye.sub(target, d);
        radius = d.length();
        elevationDeg = (float) Math.toDegrees(Math.asin(d.y / radius));
        azimuthDeg = (float) Math.toDegrees(Math.atan2(d.x, d.z));
    }

    // =====================================================================
    //  View Matrix (LookAt) -- perhitungan manual
    // =====================================================================

    /**
     * Membentuk basis VCS:
     * <pre>
     *   n = (E - At) / |E - At|      (View Plane Normal, kamera melihat ke -n)
     *   u = (Up x n) / |Up x n|      (Right)
     *   v = n x u                    (True Up)
     * </pre>
     */
    private void computeBasis(Vector3f n, Vector3f u, Vector3f v) {
        eye.sub(target, n).normalize();
        up.cross(n, u);
        if (u.lengthSquared() < 1.0e-10f) {          // up sejajar arah pandang -> pakai sumbu cadangan
            new Vector3f(0f, 0f, 1f).cross(n, u);
        }
        u.normalize();
        n.cross(u, v);
    }

    /** Matriks View (World Space -> View Space), dihitung manual. */
    public Matrix4f calculateLookAtMatrix() {
        Vector3f n = new Vector3f();
        Vector3f u = new Vector3f();
        Vector3f v = new Vector3f();
        computeBasis(n, u, v);

        // JOML bersifat column-major: m{kolom}{baris}
        Matrix4f view = new Matrix4f();
        view.m00(u.x).m10(u.y).m20(u.z).m30(-u.dot(eye));
        view.m01(v.x).m11(v.y).m21(v.z).m31(-v.dot(eye));
        view.m02(n.x).m12(n.y).m22(n.z).m32(-n.dot(eye));
        view.m03(0.0f).m13(0.0f).m23(0.0f).m33(1.0f);
        return view;
    }

    /**
     * Matriks View untuk satu mata pada rendering stereo. Mata digeser sejauh
     * {@code eyeOffset} sepanjang sumbu kanan kamera (negatif = mata kiri,
     * positif = mata kanan); arah pandang tetap paralel (off-axis, bukan toe-in).
     */
    public Matrix4f calculateStereoViewMatrix(float eyeOffset) {
        Matrix4f shift = new Matrix4f().translation(-eyeOffset, 0f, 0f);
        return shift.mul(calculateLookAtMatrix());
    }

    // =====================================================================
    //  Kamera orbital
    // =====================================================================

    /** Perbarui posisi Eye dari koordinat sferis di sekitar Target. */
    public void updateOrbitalPosition(float radius, float azimuthDeg, float elevationDeg) {
        this.radius = clamp(radius, MIN_RADIUS, MAX_RADIUS);
        this.azimuthDeg = azimuthDeg;
        this.elevationDeg = clamp(elevationDeg, MIN_ELEVATION_DEG, MAX_ELEVATION_DEG);

        float azRad = (float) Math.toRadians(this.azimuthDeg);
        float elRad = (float) Math.toRadians(this.elevationDeg);
        eye.x = target.x + this.radius * (float) (Math.cos(elRad) * Math.sin(azRad));
        eye.y = target.y + this.radius * (float) Math.sin(elRad);
        eye.z = target.z + this.radius * (float) (Math.cos(elRad) * Math.cos(azRad));
    }

    // =====================================================================
    //  Getter (mengembalikan salinan agar state tetap terenkapsulasi)
    // =====================================================================

    public Vector3f getEye()    { return new Vector3f(eye); }
    public Vector3f getTarget() { return new Vector3f(target); }
    public Vector3f getUp()     { return new Vector3f(up); }

    /** Arah pandang ternormalisasi (dari Eye menuju Target). */
    public Vector3f getForward() {
        Vector3f f = new Vector3f();
        target.sub(eye, f);
        return f.normalize();
    }

    public float getRadius()       { return radius; }
    public float getAzimuthDeg()   { return azimuthDeg; }
    public float getElevationDeg() { return elevationDeg; }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
