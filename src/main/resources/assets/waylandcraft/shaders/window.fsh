#version 330

layout(std140) uniform WindowInfo {
	mat4 transform;
	float alphaBlend;
};

uniform sampler2D Sampler0;

in vec2 texCoord;

out vec4 fragColor;

void main() {
	vec4 color = texture(Sampler0, texCoord);
	color.a = color.a + alphaBlend * (1 - color.a);
	if(color.a == 0.0) {
		discard;
	}
	fragColor = color;
}