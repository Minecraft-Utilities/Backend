#version 330 core

layout(location = 0) in vec3 a_position;
layout(location = 1) in vec2 a_uv;
layout(location = 2) in vec3 a_normal;

uniform mat4 u_mvp;
uniform mat3 u_normalMatrix;

out vec2 v_uv;
out vec3 v_normal;

void main() {
    gl_Position = u_mvp * vec4(a_position, 1.0);
    v_uv = a_uv;
    v_normal = u_normalMatrix * a_normal;
}
