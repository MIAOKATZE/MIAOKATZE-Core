#version 130
// SPDX-License-Identifier: AGPL-3.0-only
uniform vec4 rowsA;
uniform vec4 rowsB;
uniform vec3 ink;
uniform vec3 lightInk;
uniform int motif;
uniform float phase;
in vec2 sealPixel;
out vec4 sealColor;
float distanceOnRing(float x) { return abs(fract(x + 0.5) - 0.5); }
void main() {
    ivec2 p = clamp(ivec2(floor(sealPixel)), ivec2(0), ivec2(7));
    int row = int(p.y < 4 ? rowsA[p.y] : rowsB[p.y-4]);
    if (((row >> (7 - p.x)) & 1) == 0) discard;
    float x = (float(p.x)+0.5)/8.0, y = (float(p.y)+0.5)/8.0;
    float travel = x+y*0.25;
    if (motif == 0) travel = x+y;                           // quiet register
    if (motif == 1) travel = y < 0.49 ? y*0.55 : x*0.55+0.35; // grid junction
    if (motif == 2) travel = x+floor(y*4.0)*0.19;           // offset packets
    if (motif == 3) travel = (p.x+p.y < 8 ? 0.25 : 0.75); // paired orbitals
    if (motif == 4) travel = 1.0-y+x*0.18;                 // steam rising
    if (motif == 5) travel = y+(p.x < 4 ? 0.20 : -0.20);  // two lips of a rift
    if (motif == 6) travel = floor((x*0.62+y)*3.0)/3.0;   // facet by facet
    if (motif == 7) travel = p.y < 3 ? x*0.3 : 0.65;      // tips of ears
    if (motif == 8) travel = (x-y)*0.65;                   // diagonal coin glint
    if (motif == 9) travel = length(vec2(x-0.5,y-0.65));   // warm centre
    if (motif == 10) travel = (p.y < 4 ? x : 1.0-y)*0.65; // horizontal then vertical rule
    float glint = 1.0-smoothstep(0.03,0.25,distanceOnRing(travel-phase));
    sealColor = vec4(mix(ink,lightInk,0.15+0.80*glint),1.0);
}
