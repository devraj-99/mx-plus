package com.mxplus.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Glass surface. With a [haze] state it blurs the content behind it (works over
 * Compose content and over TextureView video). With a Kyant [backdrop] and a
 * corner-based shape it bends light through the refraction lens.
 * Flat colours only: no gradients, no glow.
 */
@Composable
fun Modifier.glass(shape: Shape, haze: HazeState? = null, dark: Boolean = true, backdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null, overVideo: Boolean = false): Modifier {
    val opacity = Prefs.glassOpacity.coerceIn(0.05f, 0.85f)
    val tint = if (overVideo) Color.White.copy(alpha = opacity * 0.35f) else if (dark) Color.Black.copy(alpha = opacity * 0.7f) else Color.White.copy(alpha = opacity)
    val edge = if (dark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.70f)
    val fill = if (overVideo) Color.White.copy(alpha = opacity * 0.5f) else if (dark) Color.Black.copy(alpha = opacity) else Color.White.copy(alpha = (opacity + 0.25f).coerceAtMost(0.85f))
    if (Prefs.glass && backdrop != null && shape is androidx.compose.foundation.shape.CornerBasedShape && com.kyant.backdrop.isRuntimeShaderSupported()) {
        return this.lensGlass(backdrop, shape, Prefs.glassStrength)
    }
    return if (Prefs.glass && haze != null) {
        this
            .clip(shape)
            .hazeEffect(state = haze) {
                blurRadius = Prefs.glassBlur.coerceIn(0f, 60f).dp
                tints = listOf(HazeTint(tint))
            }
            .border(1.dp, edge, shape)
    } else if (Prefs.glass) {
        this.clip(shape).background(fill).border(1.dp, edge, shape)
    } else {
        this.clip(shape).background(if (dark) Color(0xFF1C1C1F) else Color.White).border(1.dp, edge, shape)
    }
}

/** Touch reaction like Apple's interactive glass: the control shrinks a little while pressed. */
@Composable
fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "press")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}
