// ==========================================================
// FILE: src/main/resources/shaders/phong.frag (Fragment Shader)
// Phong / Blinn-Phong per-pixel + Texture + Shadow Mapping (PCF 3x3)
// ==========================================================
#version 330 core
out vec4 FragColor;

in vec3 FragPos;
in vec3 Normal;
in vec2 TexCoords;
in vec3 VertColor;
in vec4 FragPosLightSpace;

uniform vec3  lightDir;        // arah MENUJU sumber cahaya (ternormalisasi) -> Directional Light
uniform vec3  viewPos;
uniform vec3  lightColor;
uniform vec3  objectColor;
uniform sampler2D textureSampler;   // unit 0 : tekstur objek
uniform sampler2D shadowMap;        // unit 1 : depth map dari sudut cahaya
uniform bool  useTexture;
uniform bool  useBlinnPhong;
uniform bool  useShadow;
uniform bool  unlit;           // true = tampilkan warna polos (penanda posisi cahaya)
uniform float shadowBias;      // bias dasar (bisa diubah real-time)

// ---------------------------------------------------------------
// Shadow Mapping: bandingkan kedalaman fragmen dengan Shadow Map.
// Mengembalikan 0.0 (terang) .. 1.0 (bayangan penuh), dilunakkan PCF 3x3.
// ---------------------------------------------------------------
float calcShadow(vec4 posLightSpace, vec3 N, vec3 L)
{
    vec3 p = posLightSpace.xyz / posLightSpace.w;   // perspective divide (ortho: w = 1)
    p = p * 0.5 + 0.5;                              // NDC [-1,1] -> [0,1]
    if (p.z > 1.0) return 0.0;                      // di luar far plane cahaya

    // Slope-scaled bias: permukaan miring terhadap cahaya butuh bias lebih besar
    float bias = max(shadowBias * 10.0 * (1.0 - dot(N, L)), shadowBias);

    float shadow = 0.0;
    vec2 texel = 1.0 / vec2(textureSize(shadowMap, 0));
    for (int x = -1; x <= 1; ++x) {
        for (int y = -1; y <= 1; ++y) {
            float dShadow = texture(shadowMap, p.xy + vec2(x, y) * texel).r;
            shadow += (p.z - bias > dShadow) ? 1.0 : 0.0;
        }
    }
    return shadow / 9.0;
}

void main()
{
    vec3 baseColor = (useTexture ? texture(textureSampler, TexCoords).rgb : objectColor) * VertColor;

    if (unlit) {
        FragColor = vec4(objectColor, 1.0);
        return;
    }

    // 1. Ambient Lighting
    float ambientStrength = 0.2;
    vec3 ambient = ambientStrength * lightColor;

    // 2. Diffuse Lighting (Lambertian)
    vec3 norm = normalize(Normal);
    vec3 L = normalize(lightDir);
    float diff = max(dot(norm, L), 0.0);
    vec3 diffuse = diff * lightColor;

    // 3. Specular Lighting (Phong vs Blinn-Phong)
    float specularStrength = 0.8;
    vec3 viewDir = normalize(viewPos - FragPos);
    float spec = 0.0;
    if (diff > 0.0) {
        if (useBlinnPhong) {
            vec3 halfwayDir = normalize(L + viewDir);
            spec = pow(max(dot(norm, halfwayDir), 0.0), 32.0);   // Blinn-Phong  (N . H)^n
        } else {
            vec3 reflectDir = reflect(-L, norm);
            spec = pow(max(dot(viewDir, reflectDir), 0.0), 16.0); // Phong        (R . V)^n
        }
    }
    vec3 specular = specularStrength * spec * lightColor;

    // 4. Shadow: ambient tidak ikut digelapkan; diffuse + specular dikali Shadow Factor (0.2 .. 1.0)
    float shadowFactor = 1.0;
    if (useShadow) {
        shadowFactor = mix(1.0, 0.2, calcShadow(FragPosLightSpace, norm, L));
    }

    vec3 result = (ambient + shadowFactor * (diffuse + specular)) * baseColor;
    FragColor = vec4(result, 1.0);
}
