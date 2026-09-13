#version 330

layout(std140) uniform WindowInfo {
	mat4 transform;
	float alphaBlend;
};

in vec3 Position;
in vec2 UV0;

out vec2 texCoord;

void main() {
	gl_Position = transform * vec4(Position, 1.0);
	texCoord = UV0;
}