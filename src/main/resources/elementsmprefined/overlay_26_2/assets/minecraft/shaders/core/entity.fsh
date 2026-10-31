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
