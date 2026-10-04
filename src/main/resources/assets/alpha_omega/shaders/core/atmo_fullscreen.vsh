#version 150

// A full-screen quad given directly in clip space, at the far plane.

in vec3 Position;

out vec2 screenPos;

void main() {
    gl_Position = vec4(Position.xy, 1.0, 1.0);
    screenPos = Position.xy;
}
