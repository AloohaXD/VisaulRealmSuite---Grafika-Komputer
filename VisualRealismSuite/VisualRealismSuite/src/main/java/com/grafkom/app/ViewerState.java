package com.grafkom.app;

import com.grafkom.camera.Camera3D;
import com.grafkom.camera.OrbitalCameraController;
import com.grafkom.projection.PerspectiveProjection;
import com.grafkom.render.RealismRenderer;
import java.util.Locale;

/**
 * Menyatukan komponen aplikasi (kamera orbital Minggu 10 + {@link RealismRenderer} Minggu 11)
 * dan menyediakan aksi tingkat tinggi yang dipanggil oleh {@link InputHandler}.
 */
public class ViewerState {

    private static final float DEFAULT_FOV = 45.0f;
    private static final float DEFAULT_NEAR = 0.1f;
    private static final float DEFAULT_FAR = 100.0f;

    private final OrbitalCameraController cameraController = new OrbitalCameraController();
    private final Camera3D camera = cameraController.getCamera();
    private final PerspectiveProjection perspective =
            new PerspectiveProjection(DEFAULT_FOV, 16f / 9f, DEFAULT_NEAR, DEFAULT_FAR);
    private final RealismRenderer renderer = new RealismRenderer();

    public void initGL() { renderer.initGL(); }

    public void dispose() { renderer.dispose(); }

    // ---- frame ----

    public void update(float dt) {
        cameraController.update(dt);
        renderer.update(dt);
    }

    public void render(int width, int height) {
        perspective.setAspectRatio((float) width / Math.max(1, height));
        renderer.render(camera, perspective, width, height);
    }

    // ---- kamera ----

    public void orbitBy(float dAzimuthDeg, float dElevationDeg) { cameraController.orbitBy(dAzimuthDeg, dElevationDeg); }

    public void zoomByFactor(float factor) { cameraController.zoomByFactor(factor); }

    public void adjustNear(float delta) { perspective.setZNear(perspective.getZNear() + delta); }

    // ---- fitur Visual Realism ----

    public void toggleBlinnPhong()      { renderer.toggleBlinnPhong(); }       // B  (Indikator 1)
    public void toggleSmoothShading()   { renderer.toggleSmoothShading(); }    // F  (Indikator 2)
    public void toggleDepthTest()       { renderer.toggleDepthTest(); }        // Z  (Indikator 3)
    public void toggleCulling()         { renderer.toggleCulling(); }          // C  (Indikator 3)
    public void toggleTexture()         { renderer.toggleTexture(); }          // T  (Indikator 4)
    public void toggleFilterMode()      { renderer.toggleFilterMode(); }       // N  (Indikator 4)
    public void toggleShadows()         { renderer.toggleShadows(); }          // O  (Indikator 5)
    public void scaleShadowBias(float f){ renderer.scaleShadowBias(f); }       // [ ] (Indikator 5)
    public void toggleWireframe()       { renderer.toggleWireframe(); }        // E
    public void toggleLightAnimation()  { renderer.toggleLightAnimation(); }   // L
    public void nextObject()            { renderer.nextObject(); }             // M

    public void resetAll() {
        cameraController.reset();
        perspective.setZNear(DEFAULT_NEAR);
        renderer.reset();
    }

    // ---- HUD (judul window) ----

    private static String onOff(boolean b) { return b ? "ON" : "OFF"; }

    public String buildHudTitle(float fps) {
        StringBuilder sb = new StringBuilder(220);
        sb.append("Visual Realism | ").append(renderer.getObjectName());
        sb.append(" | ").append(renderer.isBlinnPhong() ? "Blinn-Phong" : "Phong");
        sb.append(" | ").append(renderer.isSmoothShading() ? "Smooth" : "Flat");
        sb.append(" | Z-Buffer ").append(onOff(renderer.isDepthTest()));
        sb.append(" | Cull ").append(onOff(renderer.isBackFaceCulling()));
        sb.append(" | Tex ").append(renderer.isUseTexture()
                ? (renderer.isMipmapped() ? "Mipmap" : "Nearest") : "OFF");
        sb.append(" | Shadow ").append(onOff(renderer.isShadows()));
        sb.append(String.format(Locale.US, " (bias %.4f)", renderer.getShadowBias()));
        if (renderer.isWireframe()) sb.append(" | Wire");
        if (renderer.isLightAnimating()) sb.append(" | Light spin");
        sb.append(String.format(Locale.US, " | Near %.2f | %.0f FPS | H = bantuan",
                perspective.getZNear(), fps));
        return sb.toString();
    }

    public static String helpText() {
        return String.join(System.lineSeparator(),
                "",
                "================== 3D VISUAL REALISM SUITE (Minggu 11) ==================",
                " KAMERA ORBITAL",
                "   Mouse kiri + drag ........ orbit      |  Scroll / W S ... zoom",
                "   Tombol panah ............. orbit       |  J / K .......... near plane - / +",
                " INDIKATOR 1  SHADING MODEL",
                "   B ........................ Phong  <->  Blinn-Phong",
                " INDIKATOR 2  FLAT vs SMOOTH",
                "   F ........................ Flat Shading (normal face)  <->  Smooth (normal vertex rata-rata)",
                " INDIKATOR 3  HIDDEN SURFACE REMOVAL",
                "   Z ........................ Z-Buffer Depth Test ON/OFF",
                "   C ........................ Back-Face Culling ON/OFF",
                " INDIKATOR 4  TEXTURE MAPPING",
                "   T ........................ Tekstur ON/OFF",
                "   N ........................ Filtering: Nearest  <->  Bilinear + Mipmap",
                " INDIKATOR 5  SHADOW MAPPING",
                "   O ........................ Bayangan ON/OFF",
                "   [ / ] .................... Shadow bias - / +  (kecil = Shadow Acne, besar = Peter Panning)",
                "   L ........................ Putar cahaya ON/OFF",
                " LAINNYA",
                "   M ........................ Ganti model (Sphere / Torus / Rumah 3D)",
                "   E ........................ Wireframe ON/OFF",
                "   R ........................ Reset semua   |   H ... bantuan   |   ESC ... keluar",
                "=========================================================================",
                "");
    }
}
