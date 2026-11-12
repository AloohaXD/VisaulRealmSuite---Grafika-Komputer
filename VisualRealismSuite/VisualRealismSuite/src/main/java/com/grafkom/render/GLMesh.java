package com.grafkom.render;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

import com.grafkom.model.Mesh3D;

/**
 * Buffer GPU (VAO + VBO) untuk satu {@link Mesh3D}. Layout interleaved (14 float / vertex):
 * <pre>
 *   location 0 : aPos        vec3   offset  0
 *   location 1 : aNormal     vec3   offset  3   (smooth)
 *   location 2 : aTexCoords  vec2   offset  6
 *   location 3 : aFlatNormal vec3   offset  8   (flat)
 *   location 4 : aColor      vec3   offset 11
 * </pre>
 */
public class GLMesh {

    private final Mesh3D mesh;
    private final int vao;
    private final int vbo;
    private final int vertexCount;

    public GLMesh(Mesh3D mesh) {
        this.mesh = mesh;
        this.vertexCount = mesh.getVertexCount();

        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, mesh.toInterleaved(), GL_STATIC_DRAW);

        final int stride = Mesh3D.FLOATS_PER_VERTEX * Float.BYTES;
        attrib(0, 3, stride, 0);
        attrib(1, 3, stride, 3);
        attrib(2, 2, stride, 6);
        attrib(3, 3, stride, 8);
        attrib(4, 3, stride, 11);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    private static void attrib(int location, int size, int stride, int floatOffset) {
        glVertexAttribPointer(location, size, GL_FLOAT, false, stride, (long) floatOffset * Float.BYTES);
        glEnableVertexAttribArray(location);
    }

    public Mesh3D getMesh() { return mesh; }

    public void draw() {
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
    }

    public void dispose() {
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
    }
}
