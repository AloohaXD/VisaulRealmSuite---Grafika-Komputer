package com.grafkom.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Model 3D berbasis segitiga (triangle list, 3 vertex per segitiga, tanpa index) yang
 * menyimpan SEMUA data yang dibutuhkan Visual Realism:
 *
 * <ul>
 *   <li>posisi vertex (x,y,z)</li>
 *   <li><b>normal flat</b>   : satu vektor normal tunggal per face  (Flat Shading)</li>
 *   <li><b>normal smooth</b> : rata-rata normal semua face yang berbagi vertex
 *       {@code N_vertex = sum(N_face_i) / ||sum(N_face_i)||}   (Smooth Shading)</li>
 *   <li>koordinat tekstur UV (u,v)</li>
 *   <li>warna tint per-vertex (putih = netral)</li>
 * </ul>
 *
 * <p>Kelas ini hanya menyimpan data (tidak menyentuh OpenGL): {@code Mesh3D} = data,
 * {@code GLMesh} = buffer GPU. Semua segitiga berorientasi <b>Counter-Clockwise (CCW)
 * dilihat dari luar</b>, sesuai {@code glFrontFace(GL_CCW)} pada {@code HSRManager}.</p>
 *
 * <p>Pabrik model: {@link #createSphere}, {@link #createTorus}, {@link #createHouse},
 * {@link #createGroundPlane}.</p>
 */
public final class Mesh3D {

    /** Jumlah float per vertex pada array interleaved: pos3 + smoothN3 + uv2 + flatN3 + color3. */
    public static final int FLOATS_PER_VERTEX = 14;

    private final String name;
    private final float[] positions;       // x,y,z per vertex
    private final float[] smoothNormals;   // nx,ny,nz per vertex (rata-rata)
    private final float[] flatNormals;     // nx,ny,nz per vertex (normal face)
    private final float[] uvs;             // u,v per vertex
    private final float[] colors;          // r,g,b per vertex
    private final float[] baseColor;       // warna objek bila tekstur dimatikan

    private Mesh3D(String name, float[] positions, float[] smoothNormals, float[] flatNormals,
                   float[] uvs, float[] colors, float[] baseColor) {
        this.name = name;
        this.positions = positions;
        this.smoothNormals = smoothNormals;
        this.flatNormals = flatNormals;
        this.uvs = uvs;
        this.colors = colors;
        this.baseColor = baseColor;
    }

    public String getName() { return name; }
    public float[] getPositions() { return positions; }
    public float[] getSmoothNormals() { return smoothNormals; }
    public float[] getFlatNormals() { return flatNormals; }
    public float[] getUvs() { return uvs; }
    public float[] getColors() { return colors; }
    public float[] getBaseColor() { return baseColor.clone(); }

    public int getVertexCount() { return positions.length / 3; }
    public int getTriangleCount() { return positions.length / 9; }

    /** Susun data menjadi satu array interleaved siap upload ke VBO. */
    public float[] toInterleaved() {
        int n = getVertexCount();
        float[] out = new float[n * FLOATS_PER_VERTEX];
        for (int i = 0; i < n; i++) {
            int o = i * FLOATS_PER_VERTEX;
            System.arraycopy(positions, i * 3, out, o, 3);          // location 0
            System.arraycopy(smoothNormals, i * 3, out, o + 3, 3);  // location 1
            System.arraycopy(uvs, i * 2, out, o + 6, 2);            // location 2
            System.arraycopy(flatNormals, i * 3, out, o + 8, 3);    // location 3
            System.arraycopy(colors, i * 3, out, o + 11, 3);        // location 4
        }
        return out;
    }

    // =====================================================================
    //  Pabrik model
    // =====================================================================

    /** Bola UV (latitude/longitude) berjari-jari 1, berpusat di origin (menyentuh lantai y = -1). */
    public static Mesh3D createSphere(int stacks, int slices) {
        float[] white = {1f, 1f, 1f};
        Builder b = new Builder();
        for (int i = 0; i < stacks; i++) {
            for (int j = 0; j < slices; j++) {
                float[] a = sph(i, j, stacks, slices);
                float[] bb = sph(i, j + 1, stacks, slices);
                float[] c = sph(i + 1, j + 1, stacks, slices);
                float[] d = sph(i + 1, j, stacks, slices);
                // (a,b,c) dan (a,c,d) -> CCW dilihat dari luar. Segitiga degenerate di kutub dibuang oleh Builder.
                b.triangle(a, bb, c, uv(j, i, slices, stacks, 2f), uv(j + 1, i, slices, stacks, 2f),
                        uv(j + 1, i + 1, slices, stacks, 2f), white, white, white);
                b.triangle(a, c, d, uv(j, i, slices, stacks, 2f), uv(j + 1, i + 1, slices, stacks, 2f),
                        uv(j, i + 1, slices, stacks, 2f), white, white, white);
            }
        }
        return b.build("Sphere", new float[]{1.00f, 0.55f, 0.18f});
    }

    private static float[] sph(int i, int j, int stacks, int slices) {
        double theta = Math.PI * i / stacks;          // 0 (kutub atas) .. PI (kutub bawah)
        double phi = 2.0 * Math.PI * j / slices;
        return new float[]{
                (float) (Math.sin(theta) * Math.cos(phi)),
                (float) Math.cos(theta),
                (float) (Math.sin(theta) * Math.sin(phi))};
    }

    private static float[] uv(int j, int i, int slices, int stacks, float repeatU) {
        return new float[]{repeatU * j / slices, 1f - (float) i / stacks};
    }

    /**
     * Torus (donat) rebah di lantai: R = jari-jari utama, r = jari-jari tabung.
     * Bentuk non-konveks -> bayangan berlubang dan self-shadowing terlihat jelas.
     */
    public static Mesh3D createTorus(float R, float r, int rings, int sides) {
        float[] white = {1f, 1f, 1f};
        float centerY = -1.0f + r;                    // dasar torus menyentuh y = -1
        Builder b = new Builder();
        for (int i = 0; i < rings; i++) {
            for (int j = 0; j < sides; j++) {
                float[] a = tor(i, j, rings, sides, R, r, centerY);
                float[] bb = tor(i, j + 1, rings, sides, R, r, centerY);
                float[] c = tor(i + 1, j + 1, rings, sides, R, r, centerY);
                float[] d = tor(i + 1, j, rings, sides, R, r, centerY);
                float[] ua = {4f * i / rings, (float) j / sides};
                float[] ub = {4f * i / rings, (float) (j + 1) / sides};
                float[] uc = {4f * (i + 1) / rings, (float) (j + 1) / sides};
                float[] ud = {4f * (i + 1) / rings, (float) j / sides};
                b.triangle(a, bb, c, ua, ub, uc, white, white, white);
                b.triangle(a, c, d, ua, uc, ud, white, white, white);
            }
        }
        return b.build("Torus", new float[]{0.25f, 0.65f, 0.95f});
    }

    private static float[] tor(int i, int j, int rings, int sides, float R, float r, float cy) {
        double alpha = 2.0 * Math.PI * i / rings;
        double beta = 2.0 * Math.PI * j / sides;
        double ring = R + r * Math.cos(beta);
        return new float[]{
                (float) (ring * Math.cos(alpha)),
                (float) (r * Math.sin(beta)) + cy,
                (float) (ring * Math.sin(alpha))};
    }

    /**
     * Rumah 3D (dinding, atap pelana dengan overhang, pintu, jendela) -- geometri yang sama
     * dengan Minggu 10, kini dilengkapi normal dan UV. UV tiap bidang dihitung dari kerangka
     * lokal bidang itu sendiri (planar mapping tanpa distorsi, 0.6 pengulangan per unit).
     */
    public static Mesh3D createHouse() {
        final float W = 1.0f, D = 1.3f;
        final float base = -1.0f, wallTop = 0.15f;
        final float eaveX = 1.2f, eaveY = 0.0f, eaveZ = 1.5f, ridgeY = 1.05f;

        float[] wallFront = {0.93f, 0.85f, 0.65f};
        float[] wallSide = {0.86f, 0.76f, 0.56f};
        float[] wallBack = {0.80f, 0.70f, 0.50f};
        float[] roofL = {1.00f, 0.55f, 0.45f};
        float[] roofR = {0.85f, 0.42f, 0.35f};
        float[] door = {0.45f, 0.27f, 0.12f};
        float[] window = {0.60f, 0.82f, 0.96f};
        float[] floor = {0.45f, 0.40f, 0.35f};

        Builder b = new Builder();
        // Dinding depan + gable depan
        b.quad(v(-W, base, D), v(W, base, D), v(W, wallTop, D), v(-W, wallTop, D), wallFront);
        b.tri(v(-W, wallTop, D), v(W, wallTop, D), v(0, 1.03f, D), wallFront);
        // Dinding belakang + gable belakang
        b.quad(v(W, base, -D), v(-W, base, -D), v(-W, wallTop, -D), v(W, wallTop, -D), wallBack);
        b.tri(v(W, wallTop, -D), v(-W, wallTop, -D), v(0, 1.03f, -D), wallBack);
        // Dinding samping
        b.quad(v(-W, base, -D), v(-W, base, D), v(-W, wallTop, D), v(-W, wallTop, -D), wallSide);
        b.quad(v(W, base, D), v(W, base, -D), v(W, wallTop, -D), v(W, wallTop, D), wallSide);
        // Lantai (terlihat dari bawah)
        b.quad(v(-W, base, -D), v(W, base, -D), v(W, base, D), v(-W, base, D), floor);
        // Atap pelana (overhang)
        b.quad(v(-eaveX, eaveY, eaveZ), v(0, ridgeY, eaveZ), v(0, ridgeY, -eaveZ), v(-eaveX, eaveY, -eaveZ), roofL);
        b.quad(v(eaveX, eaveY, eaveZ), v(eaveX, eaveY, -eaveZ), v(0, ridgeY, -eaveZ), v(0, ridgeY, eaveZ), roofR);
        // Pintu dan jendela depan (menjorok 0.01 agar tidak z-fighting)
        float zf = D + 0.01f;
        b.quad(v(-0.25f, base, zf), v(0.25f, base, zf), v(0.25f, -0.20f, zf), v(-0.25f, -0.20f, zf), door);
        b.quad(v(-0.80f, -0.55f, zf), v(-0.45f, -0.55f, zf), v(-0.45f, -0.10f, zf), v(-0.80f, -0.10f, zf), window);
        b.quad(v(0.45f, -0.55f, zf), v(0.80f, -0.55f, zf), v(0.80f, -0.10f, zf), v(0.45f, -0.10f, zf), window);
        // Jendela samping
        float xs = W + 0.01f;
        b.quad(v(-xs, -0.55f, -0.30f), v(-xs, -0.55f, 0.30f), v(-xs, -0.10f, 0.30f), v(-xs, -0.10f, -0.30f), window);
        b.quad(v(xs, -0.55f, 0.30f), v(xs, -0.55f, -0.30f), v(xs, -0.10f, -0.30f), v(xs, -0.10f, 0.30f), window);

        return b.build("Rumah 3D", new float[]{1f, 1f, 1f});
    }

    /**
     * Bidang lantai (ground plane) persegi, normal +Y, penerima bayangan.
     *
     * @param halfSize setengah sisi (unit dunia)
     * @param y        ketinggian bidang
     * @param repeat   jumlah pengulangan tekstur pada seluruh sisi
     */
    public static Mesh3D createGroundPlane(float halfSize, float y, float repeat) {
        float[] white = {1f, 1f, 1f};
        float s = halfSize;
        float[] a = v(-s, y, s), bb = v(s, y, s), c = v(s, y, -s), d = v(-s, y, -s);
        Builder b = new Builder();
        b.triangle(a, bb, c, new float[]{0, 0}, new float[]{repeat, 0}, new float[]{repeat, repeat}, white, white, white);
        b.triangle(a, c, d, new float[]{0, 0}, new float[]{repeat, repeat}, new float[]{0, repeat}, white, white, white);
        return b.build("Ground Plane", new float[]{0.55f, 0.58f, 0.62f});
    }

    private static float[] v(float x, float y, float z) { return new float[]{x, y, z}; }

    // =====================================================================
    //  Builder internal
    // =====================================================================

    /** Kunci posisi (dikuantisasi) agar vertex yang berimpit "di-weld" untuk rata-rata normal. */
    private record PosKey(int x, int y, int z) {
        static PosKey of(float[] p) {
            return new PosKey(Math.round(p[0] * 10000f), Math.round(p[1] * 10000f), Math.round(p[2] * 10000f));
        }
    }

    private static final class Builder {
        private final List<float[]> pos = new ArrayList<>();   // 3 float per vertex
        private final List<float[]> uvs = new ArrayList<>();   // 2 float per vertex
        private final List<float[]> cols = new ArrayList<>();  // 3 float per vertex
        private final List<float[]> faceN = new ArrayList<>(); // normal face (diulang 3x per segitiga)

        /** Segitiga dengan UV & warna eksplisit; segitiga degenerate (luas 0) dibuang. */
        void triangle(float[] p0, float[] p1, float[] p2, float[] t0, float[] t1, float[] t2,
                      float[] c0, float[] c1, float[] c2) {
            float[] n = faceNormal(p0, p1, p2);
            if (n == null) return;
            pos.add(p0); pos.add(p1); pos.add(p2);
            uvs.add(t0); uvs.add(t1); uvs.add(t2);
            cols.add(c0); cols.add(c1); cols.add(c2);
            faceN.add(n); faceN.add(n); faceN.add(n);
        }

        /** Segitiga datar: UV dari kerangka lokal bidang. */
        void tri(float[] p0, float[] p1, float[] p2, float[] color) {
            float[] n = faceNormal(p0, p1, p2);
            if (n == null) return;
            float[][] t = planarUv(n, p0, p1, new float[][]{p0, p1, p2});
            triangle(p0, p1, p2, t[0], t[1], t[2], color, color, color);
        }

        /** Quad (a,b,c,d) CCW -> dua segitiga (a,b,c) & (a,c,d), UV planar. */
        void quad(float[] a, float[] b, float[] c, float[] d, float[] color) {
            float[] n = faceNormal(a, b, c);
            if (n == null) return;
            float[][] t = planarUv(n, a, b, new float[][]{a, b, c, d});
            triangle(a, b, c, t[0], t[1], t[2], color, color, color);
            triangle(a, c, d, t[0], t[2], t[3], color, color, color);
        }

        /** u = sepanjang sisi (a->b), v = n x u  (u x v = n -> tidak ada cermin), skala 0.6 / unit. */
        private static float[][] planarUv(float[] n, float[] a, float[] b, float[][] pts) {
            float[] u = normalize(new float[]{b[0] - a[0], b[1] - a[1], b[2] - a[2]});
            float[] v = cross(n, u);
            final float scale = 0.6f;
            float[][] out = new float[pts.length][];
            for (int i = 0; i < pts.length; i++) {
                float[] d = {pts[i][0] - a[0], pts[i][1] - a[1], pts[i][2] - a[2]};
                out[i] = new float[]{dot(d, u) * scale, dot(d, v) * scale};
            }
            return out;
        }

        Mesh3D build(String name, float[] baseColor) {
            int n = pos.size();

            // --- Smooth normal: jumlahkan normal face (unit) pada tiap posisi yang berimpit, lalu normalisasi
            Map<PosKey, float[]> sum = new HashMap<>();
            for (int i = 0; i < n; i++) {
                float[] acc = sum.computeIfAbsent(PosKey.of(pos.get(i)), k -> new float[3]);
                float[] fn = faceN.get(i);
                acc[0] += fn[0]; acc[1] += fn[1]; acc[2] += fn[2];
            }

            float[] p = new float[n * 3];
            float[] sn = new float[n * 3];
            float[] fnArr = new float[n * 3];
            float[] t = new float[n * 2];
            float[] c = new float[n * 3];
            for (int i = 0; i < n; i++) {
                System.arraycopy(pos.get(i), 0, p, i * 3, 3);
                float[] smooth = normalize(sum.get(PosKey.of(pos.get(i))));
                System.arraycopy(smooth, 0, sn, i * 3, 3);
                System.arraycopy(faceN.get(i), 0, fnArr, i * 3, 3);
                System.arraycopy(uvs.get(i), 0, t, i * 2, 2);
                System.arraycopy(cols.get(i), 0, c, i * 3, 3);
            }
            return new Mesh3D(name, p, sn, fnArr, t, c, baseColor);
        }
    }

    // ---- aljabar vektor kecil ----

    private static float[] faceNormal(float[] a, float[] b, float[] c) {
        float[] e1 = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
        float[] e2 = {c[0] - a[0], c[1] - a[1], c[2] - a[2]};
        float[] n = cross(e1, e2);
        float len = (float) Math.sqrt(dot(n, n));
        if (len < 1.0e-9f) return null;               // degenerate
        return new float[]{n[0] / len, n[1] / len, n[2] / len};
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static float dot(float[] a, float[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    private static float[] normalize(float[] a) {
        float len = (float) Math.sqrt(dot(a, a));
        if (len < 1.0e-12f) return new float[]{0f, 1f, 0f};
        return new float[]{a[0] / len, a[1] / len, a[2] / len};
    }
}
