package com.mxplus.app

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.view.View
import androidx.media3.exoplayer.ExoPlayer

/** Equalizer + loudness (volume boost) attached to the ExoPlayer audio session. */
class AudioFx {
    private var eq: Equalizer? = null
    private var loud: LoudnessEnhancer? = null
    private var sessionId = 0

    fun attach(player: ExoPlayer) {
        val id = player.audioSessionId
        if (id == 0 || id == sessionId) return
        release()
        sessionId = id
        runCatching { eq = Equalizer(0, id) }
        runCatching { loud = LoudnessEnhancer(id) }
        applyAll()
    }

    fun applyAll() {
        applyEq()
        applyBoost()
    }

    fun applyEq() {
        val e = eq ?: return
        runCatching {
            e.enabled = Prefs.eqEnabled
            if (Prefs.eqEnabled) {
                val preset = Prefs.eqPreset
                if (preset in 0 until e.numberOfPresets) e.usePreset(preset.toShort())
            }
        }
    }

    fun applyBoost() {
        val l = loud ?: return
        runCatching {
            val pct = Prefs.volumeBoost.coerceIn(100, 200)
            if (pct <= 100) {
                l.enabled = false
            } else {
                // up to ~900 millibels gain for 2x
                l.setTargetGain(((pct - 100) * 9))
                l.enabled = true
            }
        }
    }

    fun presets(): List<String> = eq?.let { e -> (0 until e.numberOfPresets).map { e.getPresetName(it.toShort()) } } ?: emptyList()

    fun release() {
        runCatching { eq?.release() }
        runCatching { loud?.release() }
        eq = null
        loud = null
        sessionId = 0
    }
}

/** Brightness / contrast / saturation / hue applied to the video TextureView layer. */
object VisualEnhancer {
    fun apply(view: View) {
        val b = Prefs.bright / 100f // -0.5..0.5
        val c = Prefs.contrast / 100f // 0.5..2
        val s = Prefs.saturate / 100f // 0..2
        val h = Prefs.hue.toFloat()
        if (b == 0f && c == 1f && s == 1f && h == 0f) {
            view.setLayerType(View.LAYER_TYPE_NONE, null)
            return
        }
        val m = ColorMatrix()
        m.setSaturation(s)
        // contrast around midpoint 127.5, then brightness offset
        val cm = ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, (1 - c) * 127.5f + b * 255f,
                0f, c, 0f, 0f, (1 - c) * 127.5f + b * 255f,
                0f, 0f, c, 0f, (1 - c) * 127.5f + b * 255f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        m.postConcat(cm)
        if (h != 0f) {
            val rm = ColorMatrix()
            rm.setRotate(0, h)
            rm.postConcat(ColorMatrix().apply { setRotate(1, h) })
            rm.postConcat(ColorMatrix().apply { setRotate(2, h) })
            m.postConcat(rm)
        }
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(m)
        view.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
    }
}
