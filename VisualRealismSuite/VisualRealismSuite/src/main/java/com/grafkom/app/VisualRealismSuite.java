package com.grafkom.app;

import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_FORWARD_COMPAT;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_RESIZABLE;
import static org.lwjgl.glfw.GLFW.GLFW_SAMPLES;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDefaultWindowHints;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwGetTime;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSetErrorCallback;
import static org.lwjgl.glfw.GLFW.glfwSetWindowTitle;
import static org.lwjgl.glfw.GLFW.glfwShowWindow;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.glfw.GLFW.glfwSwapInterval;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.glfw.GLFW.glfwWindowShouldClose;
import static org.lwjgl.system.MemoryUtil.NULL;

import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

/**
 * Kelas utama: 3D Visual Realism Suite.
 *
 * <p>Menyiapkan window GLFW + konteks OpenGL Core 3.3, lalu menjalankan game-loop. Seluruh logika
 * kamera, proyeksi, stereo, dan scene didelegasikan ke {@link ViewerState}; input ke
 * {@link InputHandler}.</p>
 */
public class VisualRealismSuite {

    private static final int WIDTH = 1200;
    private static final int HEIGHT = 800;

    private long window;
    private ViewerState state;
    private InputHandler input;

    public static void main(String[] args) {
        new VisualRealismSuite().run();
    }

    public void run() {
        try {
            init();
            loop();
        } finally {
            cleanup();
        }
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Gagal menginisialisasi GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_SAMPLES, 4);                 // anti-aliasing MSAA 4x
        // Modern OpenGL: Core Profile 3.3 (GLSL #version 330 core). Forward-compat wajib di macOS.
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);

        window = glfwCreateWindow(WIDTH, HEIGHT, "3D Visual Realism Suite", NULL, NULL);
        if (window == NULL) {
            throw new IllegalStateException("Gagal membuat window GLFW (periksa driver OpenGL Anda)");
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);                             // V-Sync
        GL.createCapabilities();

        state = new ViewerState();
        state.initGL();

        input = new InputHandler(window, state);
        input.register();

        glfwShowWindow(window);
        System.out.println(ViewerState.helpText());
    }

    private void loop() {
        int[] fbWidth = new int[1];
        int[] fbHeight = new int[1];

        double last = glfwGetTime();
        double hudTimer = 0.0;
        int frames = 0;

        while (!glfwWindowShouldClose(window)) {
            double now = glfwGetTime();
            float dt = (float) Math.min(now - last, 0.1);   // batasi lonjakan saat window di-drag
            last = now;

            glfwPollEvents();
            input.pollContinuous(dt);
            state.update(dt);

            glfwGetFramebufferSize(window, fbWidth, fbHeight);
            if (fbWidth[0] > 0 && fbHeight[0] > 0) {         // 0 saat window di-minimize
                state.render(fbWidth[0], fbHeight[0]);
            }
            glfwSwapBuffers(window);

            frames++;
            hudTimer += dt;
            if (hudTimer >= 0.25) {
                glfwSetWindowTitle(window, state.buildHudTitle((float) (frames / hudTimer)));
                frames = 0;
                hudTimer = 0.0;
            }
        }
    }

    private void cleanup() {
        if (state != null) state.dispose();
        if (window != NULL) {
            Callbacks.glfwFreeCallbacks(window);
            glfwDestroyWindow(window);
        }
        glfwTerminate();
        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }
    }
}
