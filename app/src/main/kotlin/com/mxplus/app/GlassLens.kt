/*
   Lens shader adapted from Backdrop 2.0.0 by Kyant (Apache License 2.0), as used in NETMAX.
 */
package com.mxplus.app

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle

internal const val MxGlassLensShader = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;


float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);
    
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);
    
    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));
    
    float2 refractedCoord = coord + d * grad;
    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;
    
    half4 color = half4(0.0);
    
    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;
    
    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;
    
    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;
    
    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;
    
    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;
    
    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;
    
    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;
    
    return color;
}"""


/** Real refraction glass: bends the content behind it, adds a rim highlight. Strength comes from Settings. */
fun Modifier.lensGlass(backdrop: LayerBackdrop, shape: CornerBasedShape, strength: Float): Modifier {
    val k = strength.coerceIn(0f, 1.5f)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(saturation = 1.15f)
            blur(8.dp.toPx())
            val corner = minOf(
                shape.topStart.toPx(size, this),
                shape.topEnd.toPx(size, this),
                shape.bottomStart.toPx(size, this),
                shape.bottomEnd.toPx(size, this),
                size.minDimension / 2f,
            )
            val height = (corner * 1.1f * 0.7f).coerceIn(0f, corner.coerceAtLeast(0f))
            val amount = (size.minDimension * 0.65f * 0.6f * k).coerceIn(0f, size.minDimension)
            if (height > 0f && amount > 0f) {
                try {
                    val width = size.width
                    val surfaceHeight = size.height
                    val offset = -(padding - height).coerceAtLeast(0f)
                    padding = (padding - height).coerceAtLeast(0f)
                    runtimeShaderEffect("MxLens", MxGlassLensShader, "content") {
                        setFloatUniform("size", width, surfaceHeight)
                        setFloatUniform("offset", offset, offset)
                        setFloatUniform("cornerRadii", floatArrayOf(corner, corner, corner, corner))
                        setFloatUniform("refractionHeight", height)
                        setFloatUniform("refractionAmount", -amount)
                        setFloatUniform("depthEffect", 0.55f)
                        setFloatUniform("chromaticAberration", 0.4f * k)
                    }
                } catch (t: Throwable) {
                    // Visual effect only: never crash for it.
                }
            }
        },
        highlight = {
            Highlight(
                width = 1.dp,
                alpha = 0.7f,
                style = HighlightStyle.Default(angle = 45f),
            )
        },
        shadow = null,
        onDrawSurface = {
            drawRect(Color.White.copy(alpha = 0.25f))
        },
    ).border(1.dp, Color.White.copy(alpha = 0.24f), shape)
}
