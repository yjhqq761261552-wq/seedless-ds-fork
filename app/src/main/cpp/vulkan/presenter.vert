#version 450
layout(push_constant) uniform Draw {
    vec4 rect;
    vec4 uvRect;
    vec4 uvClamp;
    float alpha;
    int rotation;
    int filterMode;
} draw;
layout(location=0) out vec2 uv;
void main() {
    const vec2 corners[6] = vec2[6](vec2(0,0), vec2(0,1), vec2(1,1),
                                   vec2(0,0), vec2(1,1), vec2(1,0));
    vec2 c = corners[gl_VertexIndex];
    vec2 p = draw.rect.xy + c * draw.rect.zw;
    // Android pre-rotation, in Vulkan's positive-height viewport coordinates.
    if (draw.rotation == 1) p = vec2(-p.y, p.x);
    else if (draw.rotation == 2) p = -p;
    else if (draw.rotation == 3) p = vec2(p.y, -p.x);
    gl_Position = vec4(p, 0, 1);
    uv = draw.uvRect.xy + c * draw.uvRect.zw;
}
