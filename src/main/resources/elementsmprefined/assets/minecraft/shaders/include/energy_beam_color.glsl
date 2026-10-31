// elementsmp: Energy Blast, fragment stage. Needs dynamictransforms.glsl (ColorModulator) imported first.
//
// The beam / flare textures are greyscale (brightness = energy, alpha = opacity). Brightness is mapped onto a
// bloom ramp: deep green halo -> neon green body -> overbright mint -> white-hot core. The mint tier is
// pushed past 1.0 so it clamps towards white, which is what makes the centre of the beam bloom outwards.
// The beam is a set of crossed planes whose texture is a smooth lateral gradient, so the glow is soft from
// every angle; the halo fades further at glancing angles via `facing` (see energy_beam_marker.glsl).
// The plugin carries these models on a translucent block item so the client blends them (see ModelItems).

const vec3 ENERGY_HALO = vec3(0.00, 0.70, 0.10);
const vec3 ENERGY_BODY = vec3(0.05, 0.95, 0.25);
const vec3 ENERGY_MINT = vec3(0.55, 1.00, 0.65) * 1.35;

vec4 energyBeamColor(vec4 tex, float facing) {
    float lum  = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
    float body = smoothstep(0.38, 0.54, lum);   // halo -> main green body
    float mid  = smoothstep(0.66, 0.84, lum);   // bright inner ring
    float core = smoothstep(0.92, 0.99, lum);   // white-hot centre

    vec3 rgb = mix(ENERGY_HALO, ENERGY_BODY, body);
    rgb = mix(rgb, ENERGY_MINT, mid);
    rgb = mix(rgb, vec3(1.0), core);

    float alpha = tex.a * pow(facing, 1.25) * ColorModulator.a;

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
