package com.mxplus.app.photos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class StrokeItem(val points: List<Pair<Float, Float>>, val color: Int, val widthFrac: Float)
data class TextItem(val text: String, val x: Float, val y: Float, val color: Int, val sizeFrac: Float)

enum class PhotoFilter(val label: String) {
    None("Original"), Vivid("Vivid"), Warm("Warm"), Cool("Cool"), Mono("Mono"), Noir("Noir"), Fade("Fade"), Sepia("Sepia"), Dramatic("Dramatic"),
}

data class EditState(
    val rotation: Int = 0,
    val flip: Boolean = false,
    val straighten: Float = 0f,
    val aspect: Float = 0f, // 0 = original
    val exposure: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val vignette: Float = 0f,
    val filter: PhotoFilter = PhotoFilter.None,
    val strokes: List<StrokeItem> = emptyList(),
    val texts: List<TextItem> = emptyList(),
)

object PhotoEditorEngine {

    private fun filterMatrix(f: PhotoFilter): ColorMatrix {
        val m = ColorMatrix()
        when (f) {
            PhotoFilter.None -> {}
            PhotoFilter.Vivid -> {
                m.setSaturation(1.45f)
                m.postConcat(contrastMatrix(0.12f))
            }
            PhotoFilter.Warm -> m.set(floatArrayOf(1.08f, 0f, 0f, 0f, 8f, 0f, 1.0f, 0f, 0f, 0f, 0f, 0f, 0.9f, 0f, -8f, 0f, 0f, 0f, 1f, 0f))
            PhotoFilter.Cool -> m.set(floatArrayOf(0.92f, 0f, 0f, 0f, -6f, 0f, 1.0f, 0f, 0f, 0f, 0f, 0f, 1.1f, 0f, 10f, 0f, 0f, 0f, 1f, 0f))
            PhotoFilter.Mono -> m.setSaturation(0f)
            PhotoFilter.Noir -> {
                m.setSaturation(0f)
                m.postConcat(contrastMatrix(0.35f))
            }
            PhotoFilter.Fade -> {
                m.setSaturation(0.8f)
                m.postConcat(contrastMatrix(-0.2f))
                m.postConcat(ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, 18f, 0f, 1f, 0f, 0f, 18f, 0f, 0f, 1f, 0f, 18f, 0f, 0f, 0f, 1f, 0f)))
            }
            PhotoFilter.Sepia -> m.set(floatArrayOf(0.393f, 0.769f, 0.189f, 0f, 0f, 0.349f, 0.686f, 0.168f, 0f, 0f, 0.272f, 0.534f, 0.131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
            PhotoFilter.Dramatic -> {
                m.setSaturation(0.75f)
                m.postConcat(contrastMatrix(0.4f))
            }
        }
        return m
    }

    /** amount -1..1 */
    private fun contrastMatrix(amount: Float): ColorMatrix {
        val c = 1f + amount
        val t = (-0.5f * c + 0.5f) * 255f
        return ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f))
    }

    fun colorMatrix(s: EditState): ColorMatrix {
        val total = ColorMatrix()
        // exposure: multiplicative
        val e = Math.pow(2.0, (s.exposure * 1.2f).toDouble()).toFloat()
        total.postConcat(ColorMatrix(floatArrayOf(e, 0f, 0f, 0f, 0f, 0f, e, 0f, 0f, 0f, 0f, 0f, e, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
        val b = s.brightness * 80f
        total.postConcat(ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, b, 0f, 1f, 0f, 0f, b, 0f, 0f, 1f, 0f, b, 0f, 0f, 0f, 1f, 0f)))
        total.postConcat(contrastMatrix(s.contrast * 0.7f))
        val sat = ColorMatrix(); sat.setSaturation((1f + s.saturation).coerceAtLeast(0f)); total.postConcat(sat)
        val w = s.warmth * 24f
        total.postConcat(ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, w, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, -w, 0f, 0f, 0f, 1f, 0f)))
        total.postConcat(filterMatrix(s.filter))
        return total
    }

    private fun needsPixelPass(s: EditState) = s.highlights != 0f || s.shadows != 0f || s.vignette != 0f

    /** Output dimensions of the crop for a source of w x h. */
    fun outputSize(w: Int, h: Int, s: EditState): Pair<Float, Float> {
        val odd = s.rotation % 2 != 0
        val rw = if (odd) h.toFloat() else w.toFloat()
        val rh = if (odd) w.toFloat() else h.toFloat()
        val a = if (s.aspect > 0f) s.aspect else rw / rh
        return if (rw / rh > a) Pair(rh * a, rh) else Pair(rw, rw / a)
    }

    private fun coverScale(rw: Float, rh: Float, cw: Float, ch: Float, deg: Float): Float {
        val t = Math.toRadians(deg.toDouble())
        val c = cos(t).toFloat()
        val sn = sin(t).toFloat()
        var need = 1f
        for (sx in listOf(-1f, 1f)) for (sy in listOf(-1f, 1f)) {
            val x = sx * cw / 2f
            val y = sy * ch / 2f
            val xr = abs(x * c + y * sn)
            val yr = abs(-x * sn + y * c)
            need = max(need, max(xr / (rw / 2f), yr / (rh / 2f)))
        }
        return need
    }

    /** Renders the edited image so that its longer side is at most maxSide. */
    fun render(src: Bitmap, s: EditState, maxSide: Int): Bitmap {
        val odd = s.rotation % 2 != 0
        val rw = if (odd) src.height.toFloat() else src.width.toFloat()
        val rh = if (odd) src.width.toFloat() else src.height.toFloat()
        val (cw, ch) = outputSize(src.width, src.height, s)
        val k = min(1f, maxSide / max(cw, ch))
        val ow = max(1, (cw * k).toInt())
        val oh = max(1, (ch * k).toInt())
        val out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val cover = coverScale(rw, rh, cw, ch, s.straighten)
        val m = Matrix()
        m.setTranslate(-src.width / 2f, -src.height / 2f)
        m.postRotate(90f * s.rotation)
        m.postRotate(s.straighten)
        m.postScale(if (s.flip) -cover else cover, cover)
        m.postScale(k, k)
        m.postTranslate(ow / 2f, oh / 2f)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix(s))
        canvas.drawBitmap(src, m, paint)

        if (needsPixelPass(s)) pixelPass(out, s)

        // markup
        val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (st in s.strokes) {
            if (st.points.size < 2) continue
            sp.color = st.color
            sp.strokeWidth = st.widthFrac * max(ow, oh)
            val path = Path()
            path.moveTo(st.points[0].first * ow, st.points[0].second * oh)
            for (i in 1 until st.points.size) path.lineTo(st.points[i].first * ow, st.points[i].second * oh)
            canvas.drawPath(path, sp)
        }
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        for (t in s.texts) {
            tp.color = t.color
            tp.textSize = t.sizeFrac * max(ow, oh)
            tp.setShadowLayer(tp.textSize / 14f, 0f, 0f, 0x99000000.toInt())
            canvas.drawText(t.text, t.x * ow, t.y * oh, tp)
        }
        return out
    }

    private fun pixelPass(bmp: Bitmap, s: EditState) {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val lut = IntArray(256)
        for (i in 0..255) {
            val x = i / 255f
            // shadows lift dark tones, highlights pull bright tones
            var v = x
            v += s.shadows * 0.35f * (1f - x) * (1f - x) * (1f - x)
            v += s.highlights * 0.35f * x * x * x * -1f
            lut[i] = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        }
        val useLut = s.highlights != 0f || s.shadows != 0f
        val vig = s.vignette
        val cx = w / 2f
        val cy = h / 2f
        val maxD = kotlin.math.sqrt(cx * cx + cy * cy)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = px[y * w + x]
                var r = (c shr 16) and 0xFF
                var g = (c shr 8) and 0xFF
                var b = c and 0xFF
                if (useLut) {
                    r = lut[r]; g = lut[g]; b = lut[b]
                }
                if (vig != 0f) {
                    val dx = x - cx
                    val dy = y - cy
                    val d = kotlin.math.sqrt(dx * dx + dy * dy) / maxD
                    val f = 1f - vig * 0.9f * d * d
                    val ff = if (vig > 0f) f.coerceIn(0f, 1f) else (1f - vig * 0.9f * d * d * -1f).coerceIn(1f, 2f)
                    r = (r * ff).toInt().coerceIn(0, 255)
                    g = (g * ff).toInt().coerceIn(0, 255)
                    b = (b * ff).toInt().coerceIn(0, 255)
                }
                px[y * w + x] = (c and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
            }
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }
}
