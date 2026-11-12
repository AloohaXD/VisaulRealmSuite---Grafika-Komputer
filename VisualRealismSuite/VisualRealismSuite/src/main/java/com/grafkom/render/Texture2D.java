package com.grafkom.render;

import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_LINEAR_MIPMAP_LINEAR;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_REPEAT;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;
import static org.lwjgl.stb.STBImage.stbi_failure_reason;
import static org.lwjgl.stb.STBImage.stbi_image_free;
import static org.lwjgl.stb.STBImage.stbi_load_from_memory;
import static org.lwjgl.stb.STBImage.stbi_set_flip_vertically_on_load;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryStack;

/**
 * Texture Loader (Praktikum 3): membaca PNG/JPG dari classpath dengan STBImage lalu memuatnya
 * ke GPU. Mendukung dua mode filtering yang dapat dipindah saat runtime:
 *
 * <ul>
 *   <li>{@code mipmapped = false} : <b>Nearest Neighbor</b> (GL_NEAREST) -- tampilan blok/pikselasi, aliasing</li>
 *   <li>{@code mipmapped = true}  : <b>Bilinear + Mipmap</b> (GL_LINEAR_MIPMAP_LINEAR / GL_LINEAR)</li>
 * </ul>
 *
 * <p>Bila berkas tidak ditemukan / gagal di-decode, dibuat tekstur papan catur magenta-hitam
 * sebagai pengganti agar aplikasi tetap berjalan (dan kesalahannya mudah terlihat).</p>
 */
public class Texture2D {

    private final int textureID;
    private boolean mipmapped = true;

    /** @param resourcePath path classpath, mis. {@code "/textures/brick.png"} */
    public Texture2D(String resourcePath) {
        textureID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureID);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

        boolean loaded = false;
        ByteBuffer encoded = readResource(resourcePath);
        if (encoded != null) {
            stbi_set_flip_vertically_on_load(true);   // v = 0 di tepi bawah gambar (konvensi OpenGL)
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer width = stack.mallocInt(1);
                IntBuffer height = stack.mallocInt(1);
                IntBuffer channels = stack.mallocInt(1);

                ByteBuffer data = stbi_load_from_memory(encoded, width, height, channels, 4);
                if (data != null) {
                    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width.get(0), height.get(0), 0,
                            GL_RGBA, GL_UNSIGNED_BYTE, data);
                    stbi_image_free(data);
                    loaded = true;
                } else {
                    System.err.println("Gagal decode tekstur " + resourcePath + ": " + stbi_failure_reason());
                }
            }
        } else {
            System.err.println("Tekstur tidak ditemukan: " + resourcePath);
        }

        if (!loaded) uploadFallback();

        glGenerateMipmap(GL_TEXTURE_2D);
        applyFilter();
    }

    /** Papan catur magenta/hitam 64x64 sebagai tanda tekstur gagal dimuat. */
    private static void uploadFallback() {
        final int size = 64;
        ByteBuffer px = BufferUtils.createByteBuffer(size * size * 4);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean on = ((x / 8) + (y / 8)) % 2 == 0;
                px.put((byte) (on ? 255 : 0)).put((byte) 0).put((byte) (on ? 255 : 0)).put((byte) 255);
            }
        }
        px.flip();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, size, size, 0, GL_RGBA, GL_UNSIGNED_BYTE, px);
    }

    private static ByteBuffer readResource(String path) {
        try (InputStream in = Texture2D.class.getResourceAsStream(path)) {
            if (in == null) return null;
            byte[] bytes = in.readAllBytes();
            ByteBuffer buf = BufferUtils.createByteBuffer(bytes.length);
            buf.put(bytes).flip();
            return buf;
        } catch (IOException e) {
            return null;
        }
    }

    // =====================================================================

    /** Pilih mode filtering: {@code true} = Bilinear + Mipmap, {@code false} = Nearest. */
    public void setMipmapped(boolean mipmapped) {
        this.mipmapped = mipmapped;
        glBindTexture(GL_TEXTURE_2D, textureID);
        applyFilter();
    }

    public boolean isMipmapped() { return mipmapped; }

    private void applyFilter() {
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, mipmapped ? GL_LINEAR_MIPMAP_LINEAR : GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, mipmapped ? GL_LINEAR : GL_NEAREST);
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textureID);
    }

    public void dispose() { glDeleteTextures(textureID); }
}
