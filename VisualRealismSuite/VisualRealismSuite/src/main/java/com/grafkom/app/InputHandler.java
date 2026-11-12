package com.grafkom.app;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_B;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_C;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_E;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_H;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_J;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_K;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_L;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_BRACKET;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_M;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_N;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_O;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_R;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_BRACKET;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_T;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_UP;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_Z;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.lwjgl.glfw.GLFW.glfwGetCursorPos;
import static org.lwjgl.glfw.GLFW.glfwGetKey;
import static org.lwjgl.glfw.GLFW.glfwSetCursorPosCallback;
import static org.lwjgl.glfw.GLFW.glfwSetKeyCallback;
import static org.lwjgl.glfw.GLFW.glfwSetMouseButtonCallback;
import static org.lwjgl.glfw.GLFW.glfwSetScrollCallback;
import static org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose;

/**
 * Listener keyboard &amp; mouse. Aksi diskret (toggle) lewat callback;
 * aksi kontinu (orbit/zoom/near plane saat tombol ditahan) di-polling tiap frame.
 */
public class InputHandler {

    private static final float MOUSE_SENSITIVITY = 0.4f;   // derajat per piksel
    private static final float KEY_ORBIT_SPEED = 90.0f;    // derajat per detik
    private static final float KEY_ZOOM_RATE = 0.9f;       // laju eksponensial per detik
    private static final float NEAR_SPEED = 3.0f;          // unit per detik
    private static final float BIAS_STEP = 1.25f;          // faktor per penekanan [ atau ]

    private final long window;
    private final ViewerState state;

    private boolean dragging = false;
    private double lastX;
    private double lastY;

    public InputHandler(long window, ViewerState state) {
        this.window = window;
        this.state = state;
    }

    public void register() {
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS) onKeyPressed(key);
        });

        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (button != GLFW_MOUSE_BUTTON_LEFT) return;
            if (action == GLFW_PRESS) {
                double[] x = new double[1];
                double[] y = new double[1];
                glfwGetCursorPos(win, x, y);
                lastX = x[0];
                lastY = y[0];
                dragging = true;
            } else if (action == GLFW_RELEASE) {
                dragging = false;
            }
        });

        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            if (dragging) {
                state.orbitBy((float) (-(xpos - lastX) * MOUSE_SENSITIVITY),
                              (float) ((ypos - lastY) * MOUSE_SENSITIVITY));
            }
            lastX = xpos;
            lastY = ypos;
        });

        glfwSetScrollCallback(window, (win, xoffset, yoffset) ->
                state.zoomByFactor((float) Math.pow(0.9, yoffset)));
    }

    private void onKeyPressed(int key) {
        switch (key) {
            case GLFW_KEY_B -> state.toggleBlinnPhong();      // Indikator 1
            case GLFW_KEY_F -> state.toggleSmoothShading();   // Indikator 2
            case GLFW_KEY_Z -> state.toggleDepthTest();       // Indikator 3
            case GLFW_KEY_C -> state.toggleCulling();         // Indikator 3
            case GLFW_KEY_T -> state.toggleTexture();         // Indikator 4
            case GLFW_KEY_N -> state.toggleFilterMode();      // Indikator 4
            case GLFW_KEY_O -> state.toggleShadows();         // Indikator 5
            case GLFW_KEY_LEFT_BRACKET -> state.scaleShadowBias(1f / BIAS_STEP);
            case GLFW_KEY_RIGHT_BRACKET -> state.scaleShadowBias(BIAS_STEP);
            case GLFW_KEY_L -> state.toggleLightAnimation();
            case GLFW_KEY_M -> state.nextObject();
            case GLFW_KEY_E -> state.toggleWireframe();
            case GLFW_KEY_R -> state.resetAll();
            case GLFW_KEY_H -> System.out.println(ViewerState.helpText());
            case GLFW_KEY_ESCAPE -> glfwSetWindowShouldClose(window, true);
            default -> { }
        }
    }

    /** Dipanggil tiap frame: menangani tombol yang ditahan. */
    public void pollContinuous(float dt) {
        float orbit = KEY_ORBIT_SPEED * dt;
        if (down(GLFW_KEY_LEFT))  state.orbitBy(-orbit, 0f);
        if (down(GLFW_KEY_RIGHT)) state.orbitBy(orbit, 0f);
        if (down(GLFW_KEY_UP))    state.orbitBy(0f, orbit);
        if (down(GLFW_KEY_DOWN))  state.orbitBy(0f, -orbit);

        if (down(GLFW_KEY_W)) state.zoomByFactor((float) Math.exp(-KEY_ZOOM_RATE * dt));
        if (down(GLFW_KEY_S)) state.zoomByFactor((float) Math.exp(KEY_ZOOM_RATE * dt));

        if (down(GLFW_KEY_J)) state.adjustNear(-NEAR_SPEED * dt);
        if (down(GLFW_KEY_K)) state.adjustNear(NEAR_SPEED * dt);
    }

    private boolean down(int key) {
        return glfwGetKey(window, key) == GLFW_PRESS;
    }
}
