// elementsmp: Energy Blast, vertex stage.
//
// The beam and flare models are tinted with a marker colour (4063067 = 0x3DFF5B, set in items/energy_*.json).
// No other model uses it, so it identifies their vertices without touching anything else.

const vec3 ENERGY_MARKER_TINT = vec3(61.0, 255.0, 91.0) / 255.0;
const float ENERGY_MARKER_TOLERANCE = 0.006;

// 0.0 for ordinary geometry. For beam / flare vertices: how squarely the surface faces the camera
// (0.02 .. 1.0), so the fragment stage can fade the glow out at glancing angles.
float energyBeamFacing(vec3 tint, vec3 normal, vec3 position) {
    if (all(lessThan(abs(tint - ENERGY_MARKER_TINT), vec3(ENERGY_MARKER_TOLERANCE)))) {
        return clamp(abs(dot(normalize(normal), normalize(position))), 0.02, 1.0);
    }
    return 0.0;
}
