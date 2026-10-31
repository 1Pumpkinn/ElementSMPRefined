// elementsmp: Energy Blast, vertex stage.
//
// The beam and flare models are tinted with a marker colour (4063067 = 0x3DFF5B, set in items/energy_*.json).
// No other model uses it, so it identifies their vertices without touching anything else.

const vec3 ENERGY_MARKER_TINT = vec3(61.0, 255.0, 91.0) / 255.0;
const float ENERGY_MARKER_TOLERANCE = 0.006;

// How fast the glow fades when a plane is seen at a glancing angle.
//   1.0  = linear fade (old behaviour: the beam vanished when viewed from the front)
//   <1.0 = gentler fade, so planes seen almost edge-on (looking down the beam) stay visible
const float ENERGY_FACING_CURVE = 0.25;
// Lowest value `facing` can take for beam / flare vertices. Must stay > 0.0: the fragment stage uses
// "energyFacing > 0.0" to recognise beam / flare pixels.
const float ENERGY_FACING_FLOOR = 0.30;

// 0.0 for ordinary geometry. For beam / flare vertices: how squarely the surface faces the camera
// (ENERGY_FACING_FLOOR .. 1.0), so the fragment stage can fade the glow at glancing angles.
float energyBeamFacing(vec3 tint, vec3 normal, vec3 position) {
    if (all(lessThan(abs(tint - ENERGY_MARKER_TINT), vec3(ENERGY_MARKER_TOLERANCE)))) {
        float facing = abs(dot(normalize(normal), normalize(position)));
        return clamp(pow(facing, ENERGY_FACING_CURVE), ENERGY_FACING_FLOOR, 1.0);
    }
    return 0.0;
}
