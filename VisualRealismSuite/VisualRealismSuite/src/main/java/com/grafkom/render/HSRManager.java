package com.grafkom.render;

import static org.lwjgl.opengl.GL11.GL_BACK;
import static org.lwjgl.opengl.GL11.GL_CCW;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_LESS;
import static org.lwjgl.opengl.GL11.glCullFace;
import static org.lwjgl.opengl.GL11.glDepthFunc;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glFrontFace;

/**
 * Hidden Surface Removal (Praktikum 2): konfigurasi Z-Buffer Depth Test dan Back-Face Culling.
 */
public final class HSRManager {

    private HSRManager() { }

    /** Z-Buffer: fragmen lolos bila z_new &lt; Z_Buffer[x,y] (lebih dekat ke kamera). */
    public static void setupDepthTest(boolean enable) {
        if (enable) {
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LESS);
        } else {
            glDisable(GL_DEPTH_TEST);
        }
    }

    /** Back-Face Culling: buang polygon yang membelakangi kamera (winding CW pada layar). */
    public static void setupBackFaceCulling(boolean enable) {
        if (enable) {
            glEnable(GL_CULL_FACE);
            glCullFace(GL_BACK);     // buang polygon belakang
            glFrontFace(GL_CCW);     // Counter-Clockwise = tampak depan
        } else {
            glDisable(GL_CULL_FACE);
        }
    }
}
