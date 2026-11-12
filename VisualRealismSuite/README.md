# 3D Visual Realism Suite

Tugas Mandiri **Grafika Komputer - Minggu 11**: Rendering Faces pada Visual Realism
(Java 17+ OOP, LWJGL 3, JOML, Modern OpenGL Core 3.3 / GLSL `#version 330 core`, Maven).

Merupakan perluasan project **ThreeDViewing (Minggu 10)**: kelas `Camera3D`, `OrbitalCameraController`
dan `PerspectiveProjection` dipakai ulang tanpa perubahan.

## Menjalankan

```bash
cd VisualRealismSuite
mvn compile exec:exec        # atau run.bat (Windows) / ./run.sh (Linux/macOS)
```

Prasyarat: JDK 17+, Maven 3.8+, internet saat build pertama, GPU dengan OpenGL 3.3+.
Native LWJGL dipilih otomatis per-OS (macOS memakai `-XstartOnFirstThread`).

## Kontrol

| Tombol | Fungsi | Indikator |
|---|---|---|
| `B` | Phong <-> Blinn-Phong (real-time) | 1. Shading Models |
| `F` | Flat Shading <-> Smooth Shading | 2. Flat vs Smooth |
| `Z` | Z-Buffer Depth Test ON/OFF | 3. Hidden Surface Removal |
| `C` | Back-Face Culling ON/OFF | 3. Hidden Surface Removal |
| `T` | Tekstur ON/OFF | 4. Texture Mapping |
| `N` | Filtering Nearest <-> Bilinear + Mipmap | 4. Texture Mapping |
| `O` | Bayangan ON/OFF | 5. Shadows |
| `[` / `]` | Shadow bias - / + | 5. Shadows |
| `L` | Cahaya berputar ON/OFF | - |
| `M` | Ganti model: Sphere / Torus / Rumah 3D | Mesh 3D |
| `E` | Wireframe ON/OFF | - |
| Mouse kiri + drag / panah | Orbit kamera | - |
| Scroll / `W` `S` | Zoom | - |
| `J` / `K` | Near plane - / + | - |
| `R` / `H` / `Esc` | Reset / bantuan / keluar | - |

Status semua fitur tampil pada **judul window** (HUD), termasuk FPS untuk bahan laporan.

## Struktur Project

```
src/main/java/com/grafkom/
├── app/
│   ├── VisualRealismSuite.java   <- main: window GLFW (Core Profile 3.3) + game loop
│   ├── ViewerState.java          <- merangkai kamera + renderer, aksi tingkat tinggi, HUD
│   └── InputHandler.java         <- keyboard & mouse
├── camera/   Camera3D, OrbitalCameraController      <- dari Minggu 10 (tanpa perubahan)
├── projection/ PerspectiveProjection                <- dari Minggu 10 (tanpa perubahan)
├── model/
│   └── Mesh3D.java               <- data mesh: posisi, normal FLAT, normal SMOOTH, UV, warna
│                                    pabrik: Sphere, Torus, Rumah 3D, Ground Plane
└── render/
    ├── RealismRenderer.java      <- pipeline 2-pass (Depth Pass + Lighting Pass) + semua toggle
    ├── ShaderProgram.java        <- compile/link GLSL + setter uniform
    ├── GLMesh.java               <- VAO/VBO interleaved (5 atribut)
    ├── HSRManager.java           <- Z-Buffer & Back-Face Culling        [Praktikum 2]
    ├── Texture2D.java            <- STBImage loader + mipmap/filter     [Praktikum 3]
    └── ShadowMapFBO.java         <- Depth FBO 2048x2048                 [Praktikum 4]
src/main/resources/
├── shaders/ phong.vert  phong.frag  depth.vert  depth.frag             [Praktikum 1]
└── textures/ brick.png (objek)  checker.png (lantai)
```

## Penjelasan Teknis per Indikator

**1. Shading Model.** `phong.frag` menghitung ambient + diffuse (Lambert) + specular per-pixel.
`useBlinnPhong = true` memakai `(N . H)^32` dengan halfway vector `H = normalize(L + V)`;
`false` memakai `(R . V)^16` dengan `R = reflect(-L, N)`. Eksponen mengikuti modul ajar
(Blinn-Phong umumnya butuh eksponen ~4x lebih besar daripada Phong untuk highlight sebesar sama).

**2. Flat vs Smooth.** `Mesh3D` menyimpan dua normal per vertex. *Flat* = normal face tunggal.
*Smooth* = `N_vertex = sum(N_face_i) / ||sum(N_face_i)||` atas semua face yang berbagi posisi
(vertex di-weld berdasarkan posisi, sehingga seam UV pada bola ikut mulus). Vertex shader memilih
atribut sesuai uniform `useSmooth`. Pada objek bersisi tajam (rumah) Smooth Shading akan
"membulatkan" sudut - itu efek yang memang diharapkan dari rata-rata normal.

**3. HSR.** `HSRManager` mengatur `GL_DEPTH_TEST` (`GL_LESS`) dan `GL_CULL_FACE` (`GL_BACK`, `GL_CCW`).
Semua segitiga mesh berorientasi CCW dari luar. Coba: matikan `Z` pada Torus/Rumah untuk melihat
tumpang-tindih salah; matikan `C` lalu `Z` untuk melihat bagian belakang ikut tergambar.
Z-fighting: jaga rasio near/far (default 0.1 / 100); tekan `J` untuk menaikkan near plane.

**4. Texture Mapping.** `Texture2D` memuat PNG dari classpath dengan `stbi_load_from_memory`
(`flip_vertically_on_load` agar arah V benar). Koordinat UV: parametrik pada Sphere/Torus,
planar per-bidang (tanpa distorsi) pada Rumah. `N` memindah filter: `GL_NEAREST` vs
`GL_LINEAR_MIPMAP_LINEAR` + `GL_LINEAR`. Perhatikan lantai papan catur di kejauhan: Nearest
menimbulkan aliasing/moire, Mipmap menghaluskannya. Interpolasi UV perspektif-korektif dilakukan
otomatis oleh GPU.

**5. Shadow Mapping 2-pass.**
1. *Depth Pass*: objek dirender dari sudut cahaya (proyeksi ortografik, Directional Light) ke
   `ShadowMapFBO` (depth texture 2048x2048, border depth = 1.0). Pass ini dipaksa memakai
   depth test ON dan culling OFF, tidak terpengaruh toggle `Z`/`C`/`E` milik pengguna.
2. *Lighting Pass*: posisi fragmen diubah ke ruang cahaya (`lightSpaceMatrix * worldPos`),
   dibandingkan dengan Shadow Map. Bias: `max(bias * 10 * (1 - N.L), bias)` (slope-scaled),
   ditambah PCF 3x3 untuk tepi lembut. Fragmen dalam bayangan: diffuse+specular dikali 0.2.

   Bias terlalu kecil -> **Shadow Acne**; terlalu besar -> **Peter Panning**. Ubah dengan `[` `]`.

## Perbedaan dengan Project Minggu 10 / Modul Ajar

1. **Core Profile 3.3** (VAO/VBO + shader). Pipeline fixed-function Minggu 10 (`glBegin`/`glLoadMatrixf`)
   tidak ada di Core Profile, sehingga `SceneRenderer` dan `GLMatrices` diganti `RealismRenderer`
   dan `GLMesh`. Konsekuensinya, **fitur Stereo Anaglyph dan Taksonomi Proyeksi (tombol 1-6) dari
   Minggu 10 tidak dibawa** ke project ini (tidak dinilai pada tugas Minggu 11, dan tombol `Z`, `C`, `F`
   kini dipakai fitur Minggu 11). Kelas-kelasnya tetap ada di project Minggu 10.
2. **Directional Light**: `phong.frag` modul memakai `lightPos` (point light). Di sini cahaya
   directional (arah `normalize(lightPos)`), agar shading, bias, dan proyeksi ortografik shadow map
   konsisten satu sama lain.
3. Model Torus dipakai sebagai pengganti Teapot (non-konveks, bayangannya berlubang sehingga
   self-shadowing mudah diamati).
4. Tekstur dimuat dari **classpath** (bukan path file) agar jalan dari IDE maupun Maven.
5. `GL_DEPTH_COMPONENT24` dipakai untuk depth texture (modul: `GL_DEPTH_COMPONENT` tak-berukuran).

## Pemecahan Masalah

| Gejala | Solusi |
|---|---|
| `Failed to create window` | Perbarui driver GPU; pastikan mendukung OpenGL 3.3. Pada laptop hybrid gunakan GPU diskrit. |
| Tekstur magenta-hitam | Berkas `textures/*.png` tidak ditemukan di classpath; jalankan lewat Maven/IDE dari root project. |
| Lantai tidak terlihat | Kamera berada di bawah lantai (back-face culling aktif); orbit ke atas atau tekan `C`. |
| macOS `NSInternalInconsistencyException` | Tambah VM option `-XstartOnFirstThread` (otomatis lewat `mvn compile exec:exec`). |
