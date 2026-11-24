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
 * Glass surface. With a [haze] state it blurs the Compose content behind it.
 * Without one (over the video surface, which cannot be blurred) it is a flat translucent fill.
 * Flat colours only: no gradients, no glow.
 */
@Composable
fun Modifier.glass(shape: Shape, haze: HazeState? = null, dark: Boolean = true, backdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null): Modifier {
    val tint = if (dark) Color(0xFF000000).copy(alpha = 0.30f) else Color(0xFFFFFFFF).copy(alpha = 0.45f)
    val edge = if (dark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.70f)
    val fill = if (dark) Color(0xFF000000).copy(alpha = 0.42f) else Color(0xFFFFFFFF).copy(alpha = 0.70f)
    if (Prefs.glass && backdrop != null && shape is androidx.compose.foundation.shape.CornerBasedShape && com.kyant.backdrop.isRuntimeShaderSupported()) {
        return this.lensGlass(backdrop, shape, Prefs.glassStrength)
    }
    return if (Prefs.glass && haze != null) {
        this
            .clip(shape)
            .hazeEffect(state = haze) {
                blurRadius = 28.dp
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
