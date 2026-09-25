#version 150

uniform sampler2D Sampler0;
uniform float AlphaBlend;

in vec2 texCoord;

out vec4 fragColor;

void main() {
	vec4 color = texture(Sampler0, texCoord);
	color.a = color.a + AlphaBlend * (1 - color.a);
	if(color.a == 0.0) {
		discard;
	}
	fragColor = color;
}
