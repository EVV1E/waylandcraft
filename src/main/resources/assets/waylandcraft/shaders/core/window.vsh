#version 150

uniform mat4 Transform;

in vec3 Position;
in vec2 UV0;

out vec2 texCoord;

void main() {
	gl_Position = Transform * vec4(Position, 1.0);
	texCoord = UV0;
}
