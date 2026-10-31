#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in float energyFacing;

out vec4 fragColor;

// elementsmp: Energy Blast. The beam / flare textures are greyscale (brightness = energy, alpha = opacity).
// Here the brightness is mapped to a green glow with a white-hot core, and everything except the core
// fades towards the silhouette so the box-shaped beam reads as a soft cylinder of light.
vec4 energyBeamColor(vec4 tex) {
    float lum = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
    float core = smoothstep(0.90, 0.99, lum);          // centre of the beam (now bright green, not white)
    float body = smoothstep(0.35, 0.85, lum);
    // Bloom look: saturated neon green (no red/blue wash) pushed past 1.0 so it blows out and clamps.
    vec3 glow = mix(vec3(0.0, 0.70, 0.10), vec3(0.10, 1.0, 0.28), body);
    // Overbright only the glow; the core stays a vivid green (not multiplied) so it doesn't wash out to white.
    vec3 rgb = mix(glow * (1.0 + 0.9 * body), vec3(0.22, 1.0, 0.38), core);
    // Softer, wider falloff so the glow bleeds out past the beam instead of cutting off.
    float edge = pow(energyFacing, mix(0.7, 0.3, core));
    // Denser layers: 1 - (1-a)^2 lifts the see-through glow layers (a=0.47 -> 0.72, a=0.75 -> 0.94).
    float alpha = 1.0 - pow(1.0 - tex.a, 2.0);
    return vec4(rgb, alpha * edge * ColorModulator.a);
}

void main() {
    vec4 color = texture(Sampler0, texCoord0);
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

    if (energyFacing > 0.0) {
        color = energyBeamColor(color); // fullbright: no overlay, no lightmap
    } else {
        color *= vertexColor * ColorModulator;
        color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
        color *= lightMapColor;
    }

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
