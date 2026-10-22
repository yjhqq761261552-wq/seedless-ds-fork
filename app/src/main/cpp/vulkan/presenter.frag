#version 450
#extension GL_GOOGLE_include_directive : require
#include "filter_math.glsl"
layout(set=0,binding=0) uniform sampler2D screens;
layout(push_constant) uniform Draw {
    vec4 rect;
    vec4 uvRect;
    vec4 uvClamp;
    float alpha;
    int rotation;
    int filterMode;
} draw;
layout(location=0) in vec2 uv;
layout(location=0) out vec4 color;
//----------------------------------------------------------------------------------
// Fxaa3_11.h - version used in CMAA2 sample (https://github.com/GameTechDev/CMAA2)
// Original source: https://github.com/NVIDIAGameWorks/D3DSamples/blob/master/samples/FXAA/src/FXAA3_11.h
// Modifications:   Added FXAA_LUMA_SEPARATE_R8 code path for the ability to load
//                  precomputed luma from a separate R8 texture (still using gather)
//                  instead of using .a channel in the color buffer in order to
//                  enable use of formats without alpha channel (such as 
//                  R11G11B10_FLOAT for HDR displays). This approach also reduces 
//                  memory bandwidth usage and improves performance in memory bound 
//                  scenarios when compared to using alpha channel to store luma.
//----------------------------------------------------------------------------------
// File:        FXAA\src/FXAA3_11.h
// SDK Version: v1.2 
// Email:       gameworks@nvidia.com
// Site:        http://developer.nvidia.com/
//
// Copyright (c) 2014, NVIDIA CORPORATION. All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions
// are met:
//  * Redistributions of source code must retain the above copyright
//    notice, this list of conditions and the following disclaimer.
//  * Redistributions in binary form must reproduce the above copyright
//    notice, this list of conditions and the following disclaimer in the
//    documentation and/or other materials provided with the distribution.
//  * Neither the name of NVIDIA CORPORATION nor the names of its
//    contributors may be used to endorse or promote products derived
//    from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS ``AS IS'' AND ANY
// EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
// PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
// CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
// EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
// PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
// PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
// OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//
//----------------------------------------------------------------------------------
vec4 sampleScreen(sampler2D source, vec2 pos) { return texture(source,clamp(pos,draw.uvClamp.xy,draw.uvClamp.zw)); }
 float FxaaLuma(vec4 rgba) { return dot(rgba.rgb, vec3(0.299, 0.587, 0.114)); }
vec4 FxaaPixelShader(
    vec2 pos,
    vec4 fxaaConsolePosPos,
    sampler2D tex,
    sampler2D texLuma,
    sampler2D fxaaConsole360TexExpBiasNegOne,
    sampler2D fxaaConsole360TexExpBiasNegTwo,
    vec2 fxaaQualityRcpFrame,
    vec4 fxaaConsoleRcpFrameOpt,
    vec4 fxaaConsoleRcpFrameOpt2,
    vec4 fxaaConsole360RcpFrameOpt2,
    float fxaaQualitySubpix,
    float fxaaQualityEdgeThreshold,
    float fxaaQualityEdgeThresholdMin,
    float fxaaConsoleEdgeSharpness,
    float fxaaConsoleEdgeThreshold,
    float fxaaConsoleEdgeThresholdMin,
    vec4 fxaaConsole360ConstDir
) {
    vec2 posM;
    posM.x = pos.x;
    posM.y = pos.y;
        vec4 rgbyM = sampleScreen(tex, posM);
        rgbyM.w = FxaaLuma(rgbyM);
        float lumaS = FxaaLuma(sampleScreen(texLuma, posM + (vec2( 0, 1) * fxaaQualityRcpFrame.xy)));
        float lumaE = FxaaLuma(sampleScreen(texLuma, posM + (vec2( 1, 0) * fxaaQualityRcpFrame.xy)));
        float lumaN = FxaaLuma(sampleScreen(texLuma, posM + (vec2( 0,-1) * fxaaQualityRcpFrame.xy)));
        float lumaW = FxaaLuma(sampleScreen(texLuma, posM + (vec2(-1, 0) * fxaaQualityRcpFrame.xy)));
    float maxSM = max(lumaS, rgbyM.w);
    float minSM = min(lumaS, rgbyM.w);
    float maxESM = max(lumaE, maxSM);
    float minESM = min(lumaE, minSM);
    float maxWN = max(lumaN, lumaW);
    float minWN = min(lumaN, lumaW);
    float rangeMax = max(maxWN, maxESM);
    float rangeMin = min(minWN, minESM);
    float rangeMaxScaled = rangeMax * fxaaQualityEdgeThreshold;
    float range = rangeMax - rangeMin;
    float rangeMaxClamped = max(fxaaQualityEdgeThresholdMin, rangeMaxScaled);
    bool earlyExit = range < rangeMaxClamped;
    if(earlyExit)
            return rgbyM;
        float lumaNW = FxaaLuma(sampleScreen(texLuma, posM + (vec2(-1,-1) * fxaaQualityRcpFrame.xy)));
        float lumaSE = FxaaLuma(sampleScreen(texLuma, posM + (vec2( 1, 1) * fxaaQualityRcpFrame.xy)));
        float lumaNE = FxaaLuma(sampleScreen(texLuma, posM + (vec2( 1,-1) * fxaaQualityRcpFrame.xy)));
        float lumaSW = FxaaLuma(sampleScreen(texLuma, posM + (vec2(-1, 1) * fxaaQualityRcpFrame.xy)));
    float lumaNS = lumaN + lumaS;
    float lumaWE = lumaW + lumaE;
    float subpixRcpRange = 1.0/range;
    float subpixNSWE = lumaNS + lumaWE;
    float edgeHorz1 = (-2.0 * rgbyM.w) + lumaNS;
    float edgeVert1 = (-2.0 * rgbyM.w) + lumaWE;
    float lumaNESE = lumaNE + lumaSE;
    float lumaNWNE = lumaNW + lumaNE;
    float edgeHorz2 = (-2.0 * lumaE) + lumaNESE;
    float edgeVert2 = (-2.0 * lumaN) + lumaNWNE;
    float lumaNWSW = lumaNW + lumaSW;
    float lumaSWSE = lumaSW + lumaSE;
    float edgeHorz4 = (abs(edgeHorz1) * 2.0) + abs(edgeHorz2);
    float edgeVert4 = (abs(edgeVert1) * 2.0) + abs(edgeVert2);
    float edgeHorz3 = (-2.0 * lumaW) + lumaNWSW;
    float edgeVert3 = (-2.0 * lumaS) + lumaSWSE;
    float edgeHorz = abs(edgeHorz3) + edgeHorz4;
    float edgeVert = abs(edgeVert3) + edgeVert4;
    float subpixNWSWNESE = lumaNWSW + lumaNESE;
    float lengthSign = fxaaQualityRcpFrame.x;
    bool horzSpan = edgeHorz >= edgeVert;
    float subpixA = subpixNSWE * 2.0 + subpixNWSWNESE;
    if(!horzSpan) lumaN = lumaW;
    if(!horzSpan) lumaS = lumaE;
    if(horzSpan) lengthSign = fxaaQualityRcpFrame.y;
    float subpixB = (subpixA * (1.0/12.0)) - rgbyM.w;
    float gradientN = lumaN - rgbyM.w;
    float gradientS = lumaS - rgbyM.w;
    float lumaNN = lumaN + rgbyM.w;
    float lumaSS = lumaS + rgbyM.w;
    bool pairN = abs(gradientN) >= abs(gradientS);
    float gradient = max(abs(gradientN), abs(gradientS));
    if(pairN) lengthSign = -lengthSign;
    float subpixC = clamp(abs(subpixB) * subpixRcpRange, 0.0, 1.0);
    vec2 posB;
    posB.x = posM.x;
    posB.y = posM.y;
    vec2 offNP;
    offNP.x = (!horzSpan) ? 0.0 : fxaaQualityRcpFrame.x;
    offNP.y = ( horzSpan) ? 0.0 : fxaaQualityRcpFrame.y;
    if(!horzSpan) posB.x += lengthSign * 0.5;
    if( horzSpan) posB.y += lengthSign * 0.5;
    vec2 posN;
    posN.x = posB.x - offNP.x * 1.0;
    posN.y = posB.y - offNP.y * 1.0;
    vec2 posP;
    posP.x = posB.x + offNP.x * 1.0;
    posP.y = posB.y + offNP.y * 1.0;
    float subpixD = ((-2.0)*subpixC) + 3.0;
    float lumaEndN = FxaaLuma(sampleScreen(texLuma, posN));
    float subpixE = subpixC * subpixC;
    float lumaEndP = FxaaLuma(sampleScreen(texLuma, posP));
    if(!pairN) lumaNN = lumaSS;
    float gradientScaled = gradient * 1.0/4.0;
    float lumaMM = rgbyM.w - lumaNN * 0.5;
    float subpixF = subpixD * subpixE;
    bool lumaMLTZero = lumaMM < 0.0;
    lumaEndN -= lumaNN * 0.5;
    lumaEndP -= lumaNN * 0.5;
    bool doneN = abs(lumaEndN) >= gradientScaled;
    bool doneP = abs(lumaEndP) >= gradientScaled;
    if(!doneN) posN.x -= offNP.x * 1.5;
    if(!doneN) posN.y -= offNP.y * 1.5;
    bool doneNP = (!doneN) || (!doneP);
    if(!doneP) posP.x += offNP.x * 1.5;
    if(!doneP) posP.y += offNP.y * 1.5;
    if(doneNP) {
        if(!doneN) lumaEndN = FxaaLuma(sampleScreen(texLuma, posN.xy));
        if(!doneP) lumaEndP = FxaaLuma(sampleScreen(texLuma, posP.xy));
        if(!doneN) lumaEndN = lumaEndN - lumaNN * 0.5;
        if(!doneP) lumaEndP = lumaEndP - lumaNN * 0.5;
        doneN = abs(lumaEndN) >= gradientScaled;
        doneP = abs(lumaEndP) >= gradientScaled;
        if(!doneN) posN.x -= offNP.x * 2.0;
        if(!doneN) posN.y -= offNP.y * 2.0;
        doneNP = (!doneN) || (!doneP);
        if(!doneP) posP.x += offNP.x * 2.0;
        if(!doneP) posP.y += offNP.y * 2.0;
        if(doneNP) {
            if(!doneN) lumaEndN = FxaaLuma(sampleScreen(texLuma, posN.xy));
            if(!doneP) lumaEndP = FxaaLuma(sampleScreen(texLuma, posP.xy));
            if(!doneN) lumaEndN = lumaEndN - lumaNN * 0.5;
            if(!doneP) lumaEndP = lumaEndP - lumaNN * 0.5;
            doneN = abs(lumaEndN) >= gradientScaled;
            doneP = abs(lumaEndP) >= gradientScaled;
            if(!doneN) posN.x -= offNP.x * 4.0;
            if(!doneN) posN.y -= offNP.y * 4.0;
            doneNP = (!doneN) || (!doneP);
            if(!doneP) posP.x += offNP.x * 4.0;
            if(!doneP) posP.y += offNP.y * 4.0;
            if(doneNP) {
                if(!doneN) lumaEndN = FxaaLuma(sampleScreen(texLuma, posN.xy));
                if(!doneP) lumaEndP = FxaaLuma(sampleScreen(texLuma, posP.xy));
                if(!doneN) lumaEndN = lumaEndN - lumaNN * 0.5;
                if(!doneP) lumaEndP = lumaEndP - lumaNN * 0.5;
                doneN = abs(lumaEndN) >= gradientScaled;
                doneP = abs(lumaEndP) >= gradientScaled;
                if(!doneN) posN.x -= offNP.x * 12.0;
                if(!doneN) posN.y -= offNP.y * 12.0;
                doneNP = (!doneN) || (!doneP);
                if(!doneP) posP.x += offNP.x * 12.0;
                if(!doneP) posP.y += offNP.y * 12.0;
            }
        }
    }
    float dstN = posM.x - posN.x;
    float dstP = posP.x - posM.x;
    if(!horzSpan) dstN = posM.y - posN.y;
    if(!horzSpan) dstP = posP.y - posM.y;
    bool goodSpanN = (lumaEndN < 0.0) != lumaMLTZero;
    float spanLength = (dstP + dstN);
    bool goodSpanP = (lumaEndP < 0.0) != lumaMLTZero;
    float spanLengthRcp = 1.0/spanLength;
    bool directionN = dstN < dstP;
    float dstMin = min(dstN, dstP);
    bool goodSpan = directionN ? goodSpanN : goodSpanP;
    float subpixG = subpixF * subpixF;
    float pixelOffset = (dstMin * (-spanLengthRcp)) + 0.5;
    float subpixH = subpixG * fxaaQualitySubpix;
    float pixelOffsetGood = goodSpan ? pixelOffset : 0.0;
    float pixelOffsetSubpix = max(pixelOffsetGood, subpixH);
    if(!horzSpan) posM.x += pixelOffsetSubpix * lengthSign;
    if( horzSpan) posM.y += pixelOffsetSubpix * lengthSign;
        return vec4(sampleScreen(tex, posM).xyz, rgbyM.w);
}

// LCD1x / zFast LCD Vulkan adaptation from the bundled DraStic-format shaders.
// LCD1x: original public-domain code by Gigaherz, port by jdgleaver.
// zFast LCD: Copyright (C) 2017 Greg Hogan (SoltanGris42), port by jdgleaver.
// Bundled ports and this adaptation: GNU GPL version 2 or any later version.
// See assets/licenses/03-Vulkan-LCD.txt and the original assets/shaders/*.dsd.
vec3 ndsColor(vec3 rgb) {
    rgb = clamp(pow(rgb, vec3(1.91)) * 0.89, 0.0, 1.0);
    rgb = mat3(0.87, 0.10, 0.10,
               0.255, 0.645, 0.17,
              -0.125, 0.255, 0.73) * rgb;
    // Saturated blue can produce negative red; pow(negative, fractional) is
    // undefined. Clamp before gamma conversion to avoid NaNs and driver artifacts.
    return pow(clamp(rgb, 0.0, 1.0), vec3(1.0 / 1.91));
}
vec3 lcdScreen(vec2 pos) {
    // Remove this screen's atlas origin and its actual upload scale. The grid
    // follows 256x192 DS pixels, independently for each screen and PIP layer.
    // Use the interpolated UV for phase; clamp only the texture fetch at edges.
    vec2 pixel = (uv - draw.uvRect.xy) / draw.uvRect.zw * vec2(256.0, 192.0);
    float weight;
    if (draw.filterMode == 3 || draw.filterMode == 4) {
        vec2 angle = 6.283185307179586 * (pixel - 0.25);
        weight = ((16.0 + sin(angle.y)) / 17.0) * ((4.0 + sin(angle.x)) / 5.0);
    } else {
        vec2 distanceFromCenter = abs(floor(pixel) + 0.5 - pixel);
        float y = max(distanceFromCenter.x, distanceFromCenter.y);
        y *= y;
        float yy = y * y;
        weight = 1.0 - 14.0 * (yy - 2.7 * yy * y);
    }
    vec3 rgb = sampleScreen(screens, pos).rgb;
    if (draw.filterMode == 4 || draw.filterMode == 6) rgb = ndsColor(rgb);
    return rgb * weight;
}

// Built-in interpolation family. All coordinates are local to this screen,
// not to the padded two-screen atlas. Derivatives include rotation and PIP scale.
vec2 sourceSize() {
    return max(round(draw.uvRect.zw * vec2(textureSize(screens, 0))), vec2(1.0));
}
vec3 sampleLocal(vec2 pixel, vec2 size) {
    return sampleScreen(screens, draw.uvRect.xy + pixel / size * draw.uvRect.zw).rgb;
}
vec3 fetchLocal(ivec2 pixel, vec2 size) {
    ivec2 origin = ivec2(round(draw.uvRect.xy * vec2(textureSize(screens, 0))));
    return texelFetch(screens, origin + clamp(pixel, ivec2(0), ivec2(size) - 1), 0).rgb;
}
vec3 gammaBilinear(vec2 pixel, vec2 size) {
    vec2 p = pixel - 0.5;
    ivec2 base = ivec2(floor(p));
    vec2 f = fract(p);
    vec3 a = fetchLocal(base, size), b = fetchLocal(base + ivec2(1,0), size);
    vec3 c = fetchLocal(base + ivec2(0,1), size), d = fetchLocal(base + ivec2(1,1), size);
    // Pixel AA Fast's original to_lin_fast pass squares RGB; final pass takes
    // sqrt. Fold these into four fetches, avoiding an intermediate framebuffer.
    return sqrt(max(mix(mix(a*a, b*b, f.x), mix(c*c, d*d, f.x), f.y), vec3(0.0)));
}
vec3 approximateDownscale(vec2 pixel, vec2 footprint, vec2 size, bool gamma) {
    // Four bounded taps for a screen/PIP smaller than its uploaded texture.
    // This is deliberately not advertised as exact arbitrary-ratio area filtering.
    vec2 d = vec2(footprint.x > 1.0 ? footprint.x * 0.25 : 0.0,
                  footprint.y > 1.0 ? footprint.y * 0.25 : 0.0);
    vec2 a = pixel + vec2(-d.x,-d.y), b = pixel + vec2(d.x,-d.y);
    vec2 c = pixel + vec2(-d.x,d.y), e = pixel + vec2(d.x,d.y);
    if (gamma) {
        vec3 aa=fetchLocal(ivec2(floor(a)),size), bb=fetchLocal(ivec2(floor(b)),size);
        vec3 cc=fetchLocal(ivec2(floor(c)),size), ee=fetchLocal(ivec2(floor(e)),size);
        return sqrt((aa*aa+bb*bb+cc*cc+ee*ee)*0.25);
    }
    return (sampleLocal(a,size)+sampleLocal(b,size)+sampleLocal(c,size)+sampleLocal(e,size))*0.25;
}
vec3 interpolationScreen() {
    vec2 size = sourceSize();
    vec2 pixel = (uv - draw.uvRect.xy) / draw.uvRect.zw * size;
    vec2 footprint = max(fwidth(pixel), vec2(0.000001));
    vec2 scale = 1.0 / footprint;
    vec2 coord;
    vec3 rgb;
    int mode = draw.filterMode;
    if (mode == 7 || mode == 8) {
        coord = vec2(filter_sharp_bilinear(pixel.x,scale.x), filter_sharp_bilinear(pixel.y,scale.y));
        rgb = sampleLocal(coord, size);
    } else if (mode == 9 || mode == 10) {
        if (any(greaterThan(footprint, vec2(1.0001)))) rgb = approximateDownscale(pixel, footprint, size, false);
        else {
            coord = vec2(filter_shimmerless(pixel.x,footprint.x), filter_shimmerless(pixel.y,footprint.y));
            rgb = sampleLocal(coord, size);
        }
    } else if (mode == 11) {
        if (any(greaterThan(footprint, vec2(1.0001)))) rgb = approximateDownscale(pixel, footprint, size, true);
        else {
            coord = vec2(filter_pixel_aa(pixel.x,scale.x), filter_pixel_aa(pixel.y,scale.y));
            rgb = gammaBilinear(coord, size);
        }
    } else {
        coord = vec2(filter_quilez(pixel.x), filter_quilez(pixel.y));
        rgb = sampleLocal(coord, size);
    }
    if (mode == 8 || mode == 10 || mode == 15) rgb = ndsColor(rgb);
    return rgb;
}
vec3 lcd3Screen(vec2 pos) {
    // Gigaherz LCD3x, public domain. Native DS coordinates avoid a different
    // RGB stripe density on the 3D screen, 2D screen or external display.
    vec2 pixel = (uv - draw.uvRect.xy) / draw.uvRect.zw * vec2(256.0,192.0);
    vec2 angle = 6.283185307179586 * pixel;
    const vec3 offsets = 3.141592653589793 * vec3(0.5, 0.5-2.0/3.0, 0.5-4.0/3.0);
    float yfactor = (16.0 + sin(angle.y)) / 17.0;
    vec3 xfactor = (4.0 + sin(angle.x + offsets)) / 5.0;
    vec3 rgb = sampleScreen(screens, pos).rgb;
    if (draw.filterMode == 13) rgb = ndsColor(rgb);
    return rgb * yfactor * xfactor;
}

void main() {
    vec2 pos=clamp(uv,draw.uvClamp.xy,draw.uvClamp.zw);
    vec3 rgb;
    if (draw.filterMode==2) {
        vec2 texel=1.0/vec2(textureSize(screens,0));
        rgb=FxaaPixelShader(pos,vec4(0),screens,screens,screens,screens,
            texel,vec4(0),vec4(0),vec4(0),0.75,0.166,0.0833,8.0,0.125,0.05,vec4(0)).rgb;
    } else if (draw.filterMode >= 3 && draw.filterMode <= 6) rgb=lcdScreen(pos);
    else if (draw.filterMode == 12 || draw.filterMode == 13) rgb=lcd3Screen(pos);
    else if ((draw.filterMode >= 7 && draw.filterMode <= 11) || draw.filterMode == 14 || draw.filterMode == 15) rgb=interpolationScreen();
    else rgb=sampleScreen(screens,pos).rgb;
    color=vec4(rgb,draw.alpha);
}
