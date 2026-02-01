#version 330 core

in vec2 v_uv;
in vec3 v_normal;

uniform sampler2D u_texture;
uniform vec3 u_sunDirection;
uniform float u_minBrightness;

out vec4 fragColor;

void main() {
    vec4 texColor = texture(u_texture, v_uv);
    if (texColor.a < 0.01) discard;  // Skip fully transparent pixels
    
    vec3 n = normalize(v_normal);
    float brightness = clamp(
        u_minBrightness + (1.0 - u_minBrightness) * (1.0 + dot(n, u_sunDirection)) * 0.5,
        0.0, 1.0
    );
    fragColor = vec4(texColor.rgb * brightness, texColor.a);
}
