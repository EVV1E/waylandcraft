#version 150

uniform sampler2D Sampler0;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
#ifdef RGSS
	// Rotated Grid Super Sampling Anti-Aliasing
	vec2 dx = dFdx(texCoord0);
	vec2 dy = dFdy(texCoord0);

	vec4 c1 = texture(Sampler0, texCoord0 + dx * -0.125 + dy * -0.375);
	vec4 c2 = texture(Sampler0, texCoord0 + dx *  0.375 + dy * -0.125);
	vec4 c3 = texture(Sampler0, texCoord0 + dx * -0.375 + dy *  0.125);
	vec4 c4 = texture(Sampler0, texCoord0 + dx *  0.125 + dy *  0.375);
	vec4 color = (c1 + c2 + c3 + c4) * 0.25;
#else
	vec4 color = texture(Sampler0, texCoord0);
#endif

#ifdef ALPHA_CUTOUT
	if(color.a < 0.6) {
#else
	if(color.a < 0.001) {
#endif
		discard;
	}

	color.rgb = color.rgb / color.a; // Undo framebuffer alpha premultiplication

#ifdef ALPHA_CUTOUT
	color.a = 1.0;
#endif
#ifdef NO_COLOR
	color = vec4(vec3(0.0), color.a);
#endif
	fragColor = color;
}
