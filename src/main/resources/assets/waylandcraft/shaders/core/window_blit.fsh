#version 330
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
	vec4 color = texture(Sampler0, texCoord0);
	color *= vertexColor;
	fragColor = color * ColorModulator;
}
