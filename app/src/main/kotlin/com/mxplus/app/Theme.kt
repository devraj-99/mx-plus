package com.mxplus.app

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val AccentColors: List<Pair<String, Color>> = listOf(
    "Blue" to Color(0xFF0A84FF),
    "Green" to Color(0xFF30D158),
    "Orange" to Color(0xFFFF9F0A),
    "Pink" to Color(0xFFFF375F),
    "Purple" to Color(0xFFBF5AF2),
    "Red" to Color(0xFFFF453A),
)

@Composable
fun isDarkTheme(): Boolean = when (Prefs.theme) {
    1 -> true
    2 -> false
    else -> isSystemInDarkTheme()
}

@Composable
fun MxTheme(content: @Composable () -> Unit) {
    val dark = isDarkTheme()
    val accent = AccentColors[Prefs.accent.coerceIn(0, AccentColors.size - 1)].second
    val scheme = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            background = Color(0xFF0B0B0D),
            onBackground = Color.White,
            surface = Color(0xFF161618),
            onSurface = Color.White,
            surfaceVariant = Color(0xFF242428),
            onSurfaceVariant = Color(0xFF9A9AA2),
            outline = Color(0xFF3A3A40),
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            background = Color(0xFFF4F4F7),
            onBackground = Color(0xFF0B0B0D),
            surface = Color.White,
            onSurface = Color(0xFF0B0B0D),
            surfaceVariant = Color(0xFFE8E8ED),
            onSurfaceVariant = Color(0xFF6B6B73),
            outline = Color(0xFFCFCFD6),
        )
    }
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = scheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}
