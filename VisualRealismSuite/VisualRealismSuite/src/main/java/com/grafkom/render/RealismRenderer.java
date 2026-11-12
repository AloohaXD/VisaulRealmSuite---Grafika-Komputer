package com.grafkom.render;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_FILL;
import static org.lwjgl.opengl.GL11.GL_FRONT_AND_BACK;
import static org.lwjgl.opengl.GL11.GL_LESS;
import static org.lwjgl.opengl.GL11.GL_LINE;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glDepthFunc;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glPolygonMode;

import com.grafkom.camera.Camera3D;
import com.grafkom.model.Mesh3D;
import com.grafkom.projection.PerspectiveProjection;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Pipeline rendering "3D Visual Realism Suite".
 *
 * <pre>
 *   PASS 1  Depth Pass    : scene dari sudut CAHAYA (ortografik) -> Shadow Map (Depth FBO)
 *   PASS 2  Lighting Pass : scene dari sudut KAMERA -> Phong/Blinn-Phong + Texture + Shadow lookup
 * </pre>
 *
 * <p>Semua fitur yang dinilai dapat di-toggle saat runtime lewat metode {@code toggleXxx()}
 * (dipanggil dari {@code ViewerState} oleh {@code InputHandler}).</p>
 */
public class RealismRenderer {

    // ---- geometri scene ----
    private static final float GROUND_HALF = 6.0f;
    private static final float GROUND_Y = -1.01f;     // sedikit di bawah dasar objek (y = -1)

    // ---- cahaya (Directional Light) ----
    private static final float LIGHT_DISTANCE = 10.0f;
    private static final float LIGHT_ELEVATION_DEG = 55.0f;
    private static final float DEFAULT_LIGHT_AZIMUTH = -35.0f;
    private static final float LIGHT_SPIN_DEG_PER_SEC = 30.0f;
    private static final float ORTHO_HALF = 9.0f;     // jendela ortografik cahaya (menutup lantai 12 x 12)
    private static final float LIGHT_NEAR = 1.0f;
    private static final float LIGHT_FAR = 25.0f;

    // ---- shadow bias ----
    private static final float DEFAULT_BIAS = 0.002f;
    private static final float MIN_BIAS = 0.0001f;
    private static final float MAX_BIAS = 0.05f;

    private ShaderProgram sceneShader;
    private ShaderProgram depthShader;
    private ShadowMapFBO shadowFbo;
    private Texture2D brickTexture;
    private Texture2D checkerTexture;

    private GLMesh groundMesh;
    private GLMesh lightMarker;
    private final List<GLMesh> objects = new ArrayList<>();
    private int objectIndex = 0;

    // ---- status fitur ----
    private boolean blinnPhong = true;     // B
    private boolean smoothShading = true;  // F
    private boolean depthTest = true;      // Z
    private boolean backFaceCulling = true;// C
    private boolean useTexture = true;     // T
    private boolean mipmapped = true;      // N
    private boolean shadows = true;        // O
    private boolean wireframe = false;     // E
    private boolean lightAnimating = false;// L
    private float shadowBias = DEFAULT_BIAS; // [ ]

    private float lightAzimuthDeg = DEFAULT_LIGHT_AZIMUTH;

    // =====================================================================
    //  Inisialisasi
    // =====================================================================

    public void initGL() {
        sceneShader = new ShaderProgram("/shaders/phong.vert", "/shaders/phong.frag");
        depthShader = new ShaderProgram("/shaders/depth.vert", "/shaders/depth.frag");
        shadowFbo = new ShadowMapFBO();

        brickTexture = new Texture2D("/textures/brick.png");
        checkerTexture = new Texture2D("/textures/checker.png");

        groundMesh = new GLMesh(Mesh3D.createGroundPlane(GROUND_HALF, GROUND_Y, 1.0f));
        lightMarker = new GLMesh(Mesh3D.createSphere(12, 16));
        objects.add(new GLMesh(Mesh3D.createSphere(32, 48)));
        objects.add(new GLMesh(Mesh3D.createTorus(1.0f, 0.4f, 48, 24)));
        objects.add(new GLMesh(Mesh3D.createHouse()));

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LESS);
        applyTextureFilter();
    }

    public void dispose() {
        sceneShader.dispose();
        depthShader.dispose();
        shadowFbo.dispose();
        brickTexture.dispose();
        checkerTexture.dispose();
        groundMesh.dispose();
        lightMarker.dispose();
        objects.forEach(GLMesh::dispose);
    }

    // =====================================================================
    //  Frame
    // =====================================================================

    public void update(float dt) {
        if (lightAnimating) {
            lightAzimuthDeg = (lightAzimuthDeg + LIGHT_SPIN_DEG_PER_SEC * dt) % 360f;
        }
    }

    /** Posisi cahaya (untuk penanda & arah): bola dengan radius LIGHT_DISTANCE mengelilingi origin. */
    private Vector3f lightPosition() {
        double az = Math.toRadians(lightAzimuthDeg);
        double el = Math.toRadians(LIGHT_ELEVATION_DEG);
        return new Vector3f(
                (float) (LIGHT_DISTANCE * Math.cos(el) * Math.sin(az)),
                (float) (LIGHT_DISTANCE * Math.sin(el)),
                (float) (LIGHT_DISTANCE * Math.cos(el) * Math.cos(az)));
    }

    public void render(Camera3D camera, PerspectiveProjection perspective, int width, int height) {
        Vector3f lightPos = lightPosition();
        Vector3f lightDir = new Vector3f(lightPos).normalize();   // arah MENUJU cahaya (target = origin)

        // Matriks ruang-cahaya: ortografik karena Directional Light
        Matrix4f lightView = new Matrix4f().lookAt(lightPos, new Vector3f(0f, 0f, 0f), new Vector3f(0f, 1f, 0f));
        Matrix4f lightProj = new Matrix4f().ortho(-ORTHO_HALF, ORTHO_HALF, -ORTHO_HALF, ORTHO_HALF,
                LIGHT_NEAR, LIGHT_FAR);
        Matrix4f lightSpace = new Matrix4f(lightProj).mul(lightView);

        GLMesh object = objects.get(objectIndex);

        // ================= PASS 1: DEPTH PASS (sudut cahaya) =================
        if (shadows) {
            shadowFbo.bindForWriting();

            // Pass ini WAJIB mandiri dari toggle HSR / wireframe milik pengguna,
            // kalau tidak shadow map ikut rusak saat Z / C dimatikan.
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
            HSRManager.setupDepthTest(true);
            HSRManager.setupBackFaceCulling(false);

            depthShader.bind();
            depthShader.setMat4("lightSpaceMatrix", lightSpace);
            depthShader.setMat4("model", new Matrix4f());
            object.draw();                                   // hanya objek yang melempar bayangan

            shadowFbo.unbind(width, height);
        }

        // ================= PASS 2: LIGHTING PASS (sudut kamera) =================
        glClearColor(0.07f, 0.08f, 0.10f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        HSRManager.setupDepthTest(depthTest);
        HSRManager.setupBackFaceCulling(backFaceCulling);
        glPolygonMode(GL_FRONT_AND_BACK, wireframe ? GL_LINE : GL_FILL);

        sceneShader.bind();
        sceneShader.setMat4("view", camera.calculateLookAtMatrix());
        sceneShader.setMat4("projection", perspective.calculateProjectionMatrix());
        sceneShader.setMat4("lightSpaceMatrix", lightSpace);
        sceneShader.setVec3("lightDir", lightDir);
        sceneShader.setVec3("viewPos", camera.getEye());
        sceneShader.setVec3("lightColor", 1.0f, 1.0f, 1.0f);
        sceneShader.setInt("textureSampler", 0);
        sceneShader.setInt("shadowMap", 1);
        sceneShader.setBool("useBlinnPhong", blinnPhong);
        sceneShader.setBool("useSmooth", smoothShading);
        sceneShader.setBool("useShadow", shadows);
        sceneShader.setFloat("shadowBias", shadowBias);
        sceneShader.setBool("unlit", false);
        sceneShader.setMat4("model", new Matrix4f());

        shadowFbo.bindDepthTexture(1);

        // --- lantai (penerima bayangan)
        sceneShader.setBool("useTexture", useTexture);
        sceneShader.setVec3("objectColor", groundMesh.getMesh().getBaseColor()[0],
                groundMesh.getMesh().getBaseColor()[1], groundMesh.getMesh().getBaseColor()[2]);
        checkerTexture.bind(0);
        groundMesh.draw();

        // --- objek utama
        float[] c = object.getMesh().getBaseColor();
        sceneShader.setVec3("objectColor", c[0], c[1], c[2]);
        brickTexture.bind(0);
        object.draw();

        // --- penanda posisi cahaya (tanpa lighting)
        sceneShader.setBool("unlit", true);
        sceneShader.setVec3("objectColor", 1.0f, 0.95f, 0.45f);
        sceneShader.setMat4("model", new Matrix4f().translate(lightPos).scale(0.25f));
        lightMarker.draw();

        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
    }

    // =====================================================================
    //  Toggle fitur (dipanggil dari ViewerState)
    // =====================================================================

    public void toggleBlinnPhong()   { blinnPhong = !blinnPhong; }
    public void toggleSmoothShading(){ smoothShading = !smoothShading; }
    public void toggleDepthTest()    { depthTest = !depthTest; }
    public void toggleCulling()      { backFaceCulling = !backFaceCulling; }
    public void toggleTexture()      { useTexture = !useTexture; }
    public void toggleShadows()      { shadows = !shadows; }
    public void toggleWireframe()    { wireframe = !wireframe; }
    public void toggleLightAnimation() { lightAnimating = !lightAnimating; }

    public void toggleFilterMode() {
        mipmapped = !mipmapped;
        applyTextureFilter();
    }

    private void applyTextureFilter() {
        brickTexture.setMipmapped(mipmapped);
        checkerTexture.setMipmapped(mipmapped);
    }

    public void nextObject() { objectIndex = (objectIndex + 1) % objects.size(); }

    /** Ubah bias secara multiplikatif (factor &gt; 1 = lebih besar). */
    public void scaleShadowBias(float factor) {
        shadowBias = Math.max(MIN_BIAS, Math.min(MAX_BIAS, shadowBias * factor));
    }

    public void reset() {
        blinnPhong = true;
        smoothShading = true;
        depthTest = true;
        backFaceCulling = true;
        useTexture = true;
        mipmapped = true;
        shadows = true;
        wireframe = false;
        lightAnimating = false;
        shadowBias = DEFAULT_BIAS;
        lightAzimuthDeg = DEFAULT_LIGHT_AZIMUTH;
        applyTextureFilter();
    }

    // =====================================================================
    //  Getter untuk HUD
    // =====================================================================

    public boolean isBlinnPhong()      { return blinnPhong; }
    public boolean isSmoothShading()   { return smoothShading; }
    public boolean isDepthTest()       { return depthTest; }
    public boolean isBackFaceCulling() { return backFaceCulling; }
    public boolean isUseTexture()      { return useTexture; }
    public boolean isMipmapped()       { return mipmapped; }
    public boolean isShadows()         { return shadows; }
    public boolean isWireframe()       { return wireframe; }
    public boolean isLightAnimating()  { return lightAnimating; }
    public float getShadowBias()       { return shadowBias; }
    public String getObjectName()      { return objects.get(objectIndex).getMesh().getName(); }
}
