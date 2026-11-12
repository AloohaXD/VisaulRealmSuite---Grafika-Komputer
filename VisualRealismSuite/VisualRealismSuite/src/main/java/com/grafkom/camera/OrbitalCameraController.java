package com.grafkom.camera;

import org.joml.Vector3f;

/**
 * Pengendali kamera orbital dengan interpolasi halus (exponential smoothing).
 *
 * <p>Input (mouse / keyboard) hanya mengubah nilai <i>desired</i> (target);
 * setiap frame {@link #update(float)} menggeser kamera mendekati nilai tersebut
 * sehingga gerakan terasa mulus dan independen dari frame-rate.</p>
 */
public class OrbitalCameraController {

    public static final float DEFAULT_AZIMUTH = 35.0f;
    public static final float DEFAULT_ELEVATION = 25.0f;
    public static final float DEFAULT_RADIUS = 7.0f;

    private final Camera3D camera;

    private float desiredAzimuth = DEFAULT_AZIMUTH;
    private float desiredElevation = DEFAULT_ELEVATION;
    private float desiredRadius = DEFAULT_RADIUS;

    /** Semakin besar = semakin responsif (kurang halus). */
    private float smoothing = 14.0f;

    public OrbitalCameraController() {
        this.camera = new Camera3D(new Vector3f(0f, 0f, DEFAULT_RADIUS),
                new Vector3f(0f, 0f, 0f), new Vector3f(0f, 1f, 0f));
        applyDefaults();
    }

    public Camera3D getCamera() { return camera; }

    /** Putar orbit relatif (derajat). */
    public void orbitBy(float deltaAzimuthDeg, float deltaElevationDeg) {
        desiredAzimuth += deltaAzimuthDeg;
        desiredElevation = clamp(desiredElevation + deltaElevationDeg,
                Camera3D.MIN_ELEVATION_DEG, Camera3D.MAX_ELEVATION_DEG);
    }

    /** Zoom multiplikatif: factor < 1 mendekat, factor > 1 menjauh. */
    public void zoomByFactor(float factor) {
        desiredRadius = clamp(desiredRadius * factor, Camera3D.MIN_RADIUS, Camera3D.MAX_RADIUS);
    }

    /**
     * Pindah ke sudut pandang preset melalui jalur rotasi terpendek
     * (dipakai saat memilih mode proyeksi baku).
     */
    public void snapTo(float azimuthDeg, float elevationDeg) {
        float delta = wrap180(azimuthDeg - desiredAzimuth);
        desiredAzimuth += delta;
        desiredElevation = clamp(elevationDeg, Camera3D.MIN_ELEVATION_DEG, Camera3D.MAX_ELEVATION_DEG);
    }

    /** Set radius langsung tanpa interpolasi (dipakai efek Dolly Zoom). */
    public void setRadiusImmediate(float radius) {
        desiredRadius = clamp(radius, Camera3D.MIN_RADIUS, Camera3D.MAX_RADIUS);
        camera.updateOrbitalPosition(desiredRadius, camera.getAzimuthDeg(), camera.getElevationDeg());
    }

    public float getDesiredRadius() { return desiredRadius; }

    /** Kembalikan kamera ke posisi awal. */
    public void reset() {
        applyDefaults();
    }

    private void applyDefaults() {
        desiredAzimuth = DEFAULT_AZIMUTH;
        desiredElevation = DEFAULT_ELEVATION;
        desiredRadius = DEFAULT_RADIUS;
        camera.updateOrbitalPosition(desiredRadius, desiredAzimuth, desiredElevation);
    }

    /** Dipanggil tiap frame dengan delta waktu (detik). */
    public void update(float dt) {
        float k = 1.0f - (float) Math.exp(-smoothing * dt);
        float az = camera.getAzimuthDeg() + (desiredAzimuth - camera.getAzimuthDeg()) * k;
        float el = camera.getElevationDeg() + (desiredElevation - camera.getElevationDeg()) * k;
        float r = camera.getRadius() + (desiredRadius - camera.getRadius()) * k;
        camera.updateOrbitalPosition(r, az, el);
    }

    private static float wrap180(float deg) {
        return ((deg + 180f) % 360f + 360f) % 360f - 180f;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
