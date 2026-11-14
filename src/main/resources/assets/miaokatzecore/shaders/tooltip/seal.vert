#version 130
// SPDX-License-Identifier: AGPL-3.0-only
// The parent GUI may translate, page or scale the component. Use its matrices.
uniform mat4 projection;
uniform mat4 modelView;
uniform vec2 origin;
out vec2 sealPixel;
void main() {
    int corner = gl_VertexID;
    vec2 unit = vec2(float(corner & 1), float(corner >> 1));
    sealPixel = unit * 8.0;
    gl_Position = projection * modelView * vec4(origin + sealPixel, 0.0, 1.0);
}
