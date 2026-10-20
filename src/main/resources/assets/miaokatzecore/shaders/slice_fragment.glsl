in vec3 v_CoreSliceEye;
uniform int u_CoreSliceEnabled;
uniform int u_CoreSliceFeatheredCaps;
uniform float u_CoreSliceLayerOffset;
uniform vec3 u_CoreSliceBounds; // lower, upper, inside feather width; relative to camera anchor Y
uniform vec4 u_CoreSliceWorldUp; // world +Y in eye coordinates, eye-to-anchor offset
// CORE_SLICE_CONSTANTS

void coreSliceDiscard() {
    if (u_CoreSliceEnabled == 0) return;
    float y = dot(v_CoreSliceEye, u_CoreSliceWorldUp.xyz) + u_CoreSliceWorldUp.w;
    vec3 crossNormal = cross(dFdx(v_CoreSliceEye), dFdy(v_CoreSliceEye));
    float lengthSquared = dot(crossNormal, crossNormal);
    float normalY = lengthSquared > 1e-20
        ? dot(crossNormal * inversesqrt(lengthSquared), u_CoreSliceWorldUp.xyz)
            * (gl_FrontFacing ? 1.0 : -1.0)
        : 0.0;
    if (u_CoreSliceFeatheredCaps != 0 && u_CoreSliceBounds.z > 0.0) {
        float insideY = y + (normalY >= coreSliceNormalThreshold ? -coreSliceCapEpsilon
            : normalY <= -coreSliceNormalThreshold ? coreSliceCapEpsilon : 0.0);
        // Bounds are relative to anchor Y; use the anchor's fractional part for world-aligned layers.
        y = floor(insideY + u_CoreSliceLayerOffset) + 0.5 - u_CoreSliceLayerOffset;
    }
    bool upperCap = normalY >= coreSliceNormalThreshold
        && abs(y - u_CoreSliceBounds.y) <= coreSliceCapEpsilon;
    bool lowerCap = normalY <= -coreSliceNormalThreshold
        && abs(y - u_CoreSliceBounds.x) <= coreSliceCapEpsilon;
    if ((upperCap || lowerCap) && (u_CoreSliceFeatheredCaps == 0 || u_CoreSliceBounds.z == 0.0)) return;
    if (y < u_CoreSliceBounds.x || y >= u_CoreSliceBounds.y) discard;
    float coverage = u_CoreSliceBounds.z > 0.0
        ? smoothstep(0.0, u_CoreSliceBounds.z,
            min(y - u_CoreSliceBounds.x, u_CoreSliceBounds.y - y))
        : 1.0;
    ivec2 pixel = ivec2(floor(gl_FragCoord.xy)) & ivec2(3);
    if (coverage < coreSliceBayer[pixel.y * 4 + pixel.x]) discard;
}
