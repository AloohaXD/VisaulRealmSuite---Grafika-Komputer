package com.grafkom.projection;

import org.joml.Matrix4f;

/**
 * Proyeksi perspektif yang dihitung <b>eksplisit</b> (tanpa {@code Matrix4f.perspective()})
 * dari parameter Viewing Frustum: FOV_y, Aspect Ratio, Near dan Far.
 *
 * <pre>
 *       | f/aspect  0       0                    0                  |
 *   P = |   0       f       0                    0                  |      f = 1 / tan(FOV_y / 2)
 *       |   0       0   -(far+near)/(far-near)  -2*far*near/(far-near) |
 *       |   0       0      -1                    0                  |
 * </pre>
 *
 * <p>Ditambah dukungan <i>off-axis (asymmetric) frustum</i> melalui parameter
 * pergeseran lensa (lens shift) -- dipakai oleh Perspektif 1-Titik dan oleh
 * rendering stereo.</p>
 */
public class PerspectiveProjection {

    public static final float MIN_FOV_DEG = 15.0f;
    public static final float MAX_FOV_DEG = 110.0f;

    private float fovDegree;
    private float aspectRatio;
    private float zNear;
    private float zFar;

    public PerspectiveProjection(float fovDegree, float aspectRatio, float zNear, float zFar) {
        this.fovDegree = clamp(fovDegree, MIN_FOV_DEG, MAX_FOV_DEG);
        this.aspectRatio = aspectRatio;
        this.zNear = zNear;
        this.zFar = zFar;
    }

    /** f = 1 / tan(FOV_y / 2) */
    public float getFocalScale() {
        return 1.0f / (float) Math.tan(Math.toRadians(fovDegree) / 2.0);
    }

    /** Matriks proyeksi perspektif simetris. */
    public Matrix4f calculateProjectionMatrix() {
        return calculateProjectionMatrix(0f, 0f);
    }

    /**
     * Matriks proyeksi dengan pergeseran lensa (asymmetric frustum).
     *
     * @param shiftNdcX pergeseran citra horizontal dalam satuan NDC (+ = citra bergeser ke kanan)
     * @param shiftNdcY pergeseran citra vertikal dalam satuan NDC (+ = citra bergeser ke atas)
     */
    public Matrix4f calculateProjectionMatrix(float shiftNdcX, float shiftNdcY) {
        float f = getFocalScale();

        Matrix4f proj = new Matrix4f();
        proj.zero();
        proj.m00(f / aspectRatio);
        proj.m11(f);
        // Elemen baris-0/kolom-2 dan baris-1/kolom-2 membentuk frustum asimetris:
        // x_ndc = f/aspect * x / (-z) - m20  ->  m20 = -shift
        proj.m20(-shiftNdcX);
        proj.m21(-shiftNdcY);
        proj.m22(-(zFar + zNear) / (zFar - zNear));
        proj.m32(-(2.0f * zFar * zNear) / (zFar - zNear));
        proj.m23(-1.0f);
        proj.m33(0.0f);
        return proj;
    }

    /**
     * Pergeseran citra horizontal (NDC) agar bidang pada jarak {@code focalLength}
     * berimpit di kedua mata (zero parallax) -- inti metode Off-Axis.
     *
     * @param eyeOffset   posisi mata sepanjang sumbu kanan kamera (kiri = -IPD/2, kanan = +IPD/2)
     * @param focalLength jarak bidang zero-parallax dari kamera
     */
    public float calculateStereoShiftX(float eyeOffset, float focalLength) {
        return (getFocalScale() / aspectRatio) * eyeOffset / focalLength;
    }

    /** Tinggi bidang pandang (world units) pada jarak tertentu dari kamera. */
    public float getFrustumHeightAt(float distance) {
        return 2.0f * distance * (float) Math.tan(Math.toRadians(fovDegree) / 2.0);
    }

    // ---- Getter / Setter (pengujian interaktif FOV, near/far, dolly zoom) ----

    public float getFovDegree()   { return fovDegree; }
    public float getAspectRatio() { return aspectRatio; }
    public float getZNear()       { return zNear; }
    public float getZFar()        { return zFar; }

    public void setFovDegree(float fovDegree) {
        this.fovDegree = clamp(fovDegree, MIN_FOV_DEG, MAX_FOV_DEG);
    }

    public void setAspectRatio(float aspectRatio) {
        if (aspectRatio > 0f) this.aspectRatio = aspectRatio;
    }

    /** Near plane harus > 0 dan lebih kecil dari far plane. */
    public void setZNear(float zNear) {
        this.zNear = clamp(zNear, 0.05f, Math.min(zFar - 1.0f, 30.0f));
    }

    public void setZFar(float zFar) {
        this.zFar = Math.max(zFar, zNear + 1.0f);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
