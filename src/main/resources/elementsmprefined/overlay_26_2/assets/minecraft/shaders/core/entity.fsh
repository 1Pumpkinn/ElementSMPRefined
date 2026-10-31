#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

#ifdef DISSOLVE
uniform sampler2D DissolveMaskSampler;
#endif

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
#ifdef PER_FACE_LIGHTING
in vec4 vertexPerFaceColorBack;
in vec4 vertexPerFaceColorFront;
#else
in vec4 vertexColor;
#endif

#ifndef EMISSIVE
in vec4 lightMapColor;
#endif

#ifndef NO_OVERLAY
in vec4 overlayColor;
#endif

in vec2 texCoord0;
in float energyFacing;

out vec4 fragColor;

// elementsmp: Energy Blast. The beam / flare textures are greyscale (brightness = energy, alpha = opacity).
// Here the brightness is mapped to a green glow with a white-hot core, and everything except the core
// fades towards the silhouette so the box-shaped beam reads as a soft cylinder of light.
vec4 energyBeamColor(vec4 tex) {
    float lum = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
    float core = smoothstep(0.86, 0.97, lum);
    float body = smoothstep(0.35, 0.85, lum);
    vec3 glow = mix(vec3(0.05, 0.62, 0.16), vec3(0.28, 1.0, 0.42), body);
    vec3 rgb = mix(glow, vec3(1.0), core) * (1.0 + 0.35 * body);
    float edge = pow(energyFacing, mix(1.8, 0.6, core));
    return vec4(rgb, tex.a * edge * ColorModulator.a);
}

void main() {
    vec4 color = texture(Sampler0, texCoord0);
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

#ifdef PER_FACE_LIGHTING
    vec4 faceVertexColor = gl_FrontFacing ? vertexPerFaceColorFront : vertexPerFaceColorBack;
#else
    vec4 faceVertexColor = vertexColor;
#endif

#ifdef DISSOLVE
    if (faceVertexColor.a < texture(DissolveMaskSampler, texCoord0).a) {
        discard;
    }
    // The dissolve effect entirely replaces translucency
    faceVertexColor.a = 1.0;
#endif

    if (energyFacing > 0.0) {
        color = energyBeamColor(color); // fullbright: no overlay, no lightmap
    } else {
        color *= faceVertexColor * ColorModulator;
#ifndef NO_OVERLAY
        color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
#endif
#ifndef EMISSIVE
        color *= lightMapColor;
#endif
    }

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
