// ==========================================================
// FILE: src/main/resources/shaders/depth.vert  (Pass 1: Depth Pass dari sudut cahaya)
// ==========================================================
#version 330 core
layout (location = 0) in vec3 aPos;

uniform mat4 lightSpaceMatrix;   // P_light * V_light
uniform mat4 model;

void main()
{
    gl_Position = lightSpaceMatrix * model * vec4(aPos, 1.0);
}
