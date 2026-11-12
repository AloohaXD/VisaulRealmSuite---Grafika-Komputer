// ==========================================================
// FILE: src/main/resources/shaders/phong.vert (Vertex Shader)
// ==========================================================
#version 330 core
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec3 aNormal;       // normal SMOOTH  (rata-rata normal face per vertex)
layout (location = 2) in vec2 aTexCoords;
layout (location = 3) in vec3 aFlatNormal;   // normal FLAT    (normal face tunggal)
layout (location = 4) in vec3 aColor;        // warna tint per-vertex (putih = netral)

out vec3 FragPos;
out vec3 Normal;
out vec2 TexCoords;
out vec3 VertColor;
out vec4 FragPosLightSpace;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;
uniform mat4 lightSpaceMatrix;   // P_light * V_light
uniform bool useSmooth;          // true = Smooth Shading, false = Flat Shading

void main()
{
    vec4 worldPos = model * vec4(aPos, 1.0);
    FragPos   = worldPos.xyz;

    vec3 n = useSmooth ? aNormal : aFlatNormal;
    Normal    = mat3(transpose(inverse(model))) * n;      // Normal Matrix

    TexCoords = aTexCoords;
    VertColor = aColor;
    FragPosLightSpace = lightSpaceMatrix * worldPos;

    gl_Position = projection * view * worldPos;
}
