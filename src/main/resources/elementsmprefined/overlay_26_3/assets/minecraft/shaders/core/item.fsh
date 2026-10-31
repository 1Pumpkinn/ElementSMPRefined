#version 330
#extension GL_ARB_separate_shader_objects : require

#ifdef GLINT
#include <minecraft:globals.glsl>
#endif
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

uniform sampler2D Sampler0;

#ifdef GLINT
uniform sampler2D GlintSampler;
#endif

#ifndef OIT_ALPHA_ONLY
layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
#endif
layout(location = 2) in vec4 vertexColor;
#ifndef OIT_ALPHA_ONLY
layout(location = 3) in vec4 lightMapColor;
layout(location = 4) in vec4 overlayColor;
#endif
layout(location = 5) in vec2 texCoord0;
layout(location = 7) in float energyFacing;
#ifdef GLINT
layout(location = 6) in vec2 texCoordGlint;
#endif

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

#ifndef OIT_ALPHA_ONLY
vec4 calculateFinalColor(vec4 color) {
    if (energyFacing <= 0.0) { // energy beams are fullbright: no overlay, no lightmap
        color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
        color *= lightMapColor;
    }

    #ifdef GLINT
    vec4 glintColor = GlintAlpha * texture(GlintSampler, texCoordGlint);// Glint color modulator?
    // Matches BlendFuntion.GLINT
    color.rgb += glintColor.rgb * glintColor.rgb;
    #endif

    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif

    return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
}
#endif

// elementsmp: Energy Blast. The beam / flare textures are greyscale (brightness = energy, alpha = opacity).
// Brightness is mapped onto a bloom ramp: deep green halo -> neon green body -> overbright mint -> white-hot
// core. The mint tier is pushed past 1.0 so it clamps towards white, which is what makes the centre of the
// beam bloom outwards. The beam is a set of crossed planes whose texture is a smooth lateral gradient, so
// the glow is soft from every angle; the halo fades further at glancing angles via energyFacing.
// The plugin carries these models on a translucent block item so the client blends them (see ModelItems).
vec4 energyBeamColor(vec4 tex) {
    float lum  = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
    float body = smoothstep(0.38, 0.54, lum);   // halo -> main green body
    float mid  = smoothstep(0.66, 0.84, lum);   // bright inner ring
    float core = smoothstep(0.92, 0.99, lum);   // white-hot centre

    vec3 rgb = mix(vec3(0.00, 0.70, 0.10), vec3(0.05, 0.95, 0.25), body);
    rgb = mix(rgb, vec3(0.55, 1.00, 0.65) * 1.35, mid);
    rgb = mix(rgb, vec3(1.0), core);

    float alpha = tex.a * pow(energyFacing, 1.25) * ColorModulator.a;

    // The faintest tails are invisible anyway, but would still write depth and clip the planes behind them.
    if (alpha < 0.03) {
        discard;
    }

#ifdef ALPHA_CUTOUT
    // Safety net for a pipeline that still does not blend: alpha can only be on/off there, so draw a crisp
    // opaque tube (no stipple) instead of a speckled one.
    if (ALPHA_CUTOUT > 0.05) {
        if (alpha < 0.3) {
            discard;
        }
        alpha = 1.0;
    }
#endif

    return vec4(rgb, alpha);
}

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    #ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif

    if (energyFacing > 0.0) {
        color = energyBeamColor(color);
    } else {
        color *= vertexColor * ColorModulator;
    }

    #ifdef GLINT
    color.a = max(color.a, GlintAlpha);
    #endif

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}
