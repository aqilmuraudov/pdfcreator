package az.pdfcreator.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Fixes scanned document pages and saves the result as a new JPEG:
 * - straightens tilted pages (see [Deskew]);
 * - restores the A4 shape of pages that the scanner squashed (photos taken at an angle
 *   come out shorter/wider than the real sheet).
 */
class Straightener(private val context: Context, private val outDir: File) {

    private companion object {
        const val SQRT2 = 1.41421356f
    }

    private val loader = ImageLoader(context.contentResolver)

    /**
     * @return the uri of a fixed copy, or null if nothing needed fixing, the image is not a
     *         text document, or it could not be read.
     */
    fun straighten(uri: Uri, userRotation: Int = 0, level: Boolean = true, snapA4: Boolean = true): Uri? {
        val info = loader.info(uri, userRotation) ?: return null
        // Plenty for A4 at ~300 dpi while keeping memory in check.
        val src = loader.decode(uri, info, 3500, 3500) ?: return null
        try {
            val result = estimate(src) ?: return null
            val angle = if (level) result.correction else 0f
            val needsShape = snapA4 && a4Size(src.width, src.height) != null
            if (angle == 0f && !needsShape) return null
            val rotated = if (angle != 0f) rotate(src, angle, result.paperLuma) ?: return null else src
            val size = if (snapA4) a4Size(rotated.width, rotated.height) else null
            val shaped = if (size != null) {
                runCatching { Bitmap.createScaledBitmap(rotated, size.first, size.second, true) }.getOrNull() ?: rotated
            } else rotated
            if (shaped !== rotated && rotated !== src) rotated.recycle()
            try {
                outDir.mkdirs()
                val f = File(outDir, "${UUID.randomUUID()}.jpg")
                FileOutputStream(f).use { shaped.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                return Uri.fromFile(f)
            } finally {
                if (shaped !== src) shaped.recycle()
            }
        } finally {
            src.recycle()
        }
    }

    /**
     * New (width, height) with A4 proportions, or null if the page should stay as is.
     * Only pages squashed between A4 and square are changed: ID cards, receipts and
     * other narrow formats are left alone. The long side is stretched (never shrunk),
     * and the orientation is kept.
     */
    private fun a4Size(w: Int, h: Int): Pair<Int, Int>? {
        val portrait = h >= w
        val ratio = if (portrait) w.toFloat() / h else h.toFloat() / w // short / long
        if (ratio < 0.74f || ratio > 0.99f) return null
        return if (portrait) w to (w * SQRT2).roundToInt() else (h * SQRT2).roundToInt() to h
    }

    private fun estimate(src: Bitmap): Deskew.Result? {
        val scale = 1000f / max(src.width, src.height)
        val w = max(1, (src.width * minOf(1f, scale)).roundToInt())
        val h = max(1, (src.height * minOf(1f, scale)).roundToInt())
        val small = if (w == src.width && h == src.height) src else Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== src) small.recycle()
        val luma = IntArray(px.size) { i ->
            val c = px[i]
            (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
        }
        return Deskew.estimate(luma, w, h)
    }

    private fun rotate(src: Bitmap, degrees: Float, paperLuma: Int): Bitmap? = try {
        val m = Matrix().apply { postRotate(degrees, src.width / 2f, src.height / 2f) }
        val bounds = RectF(0f, 0f, src.width.toFloat(), src.height.toFloat())
        m.mapRect(bounds)
        m.postTranslate(-bounds.left, -bounds.top)
        val out = Bitmap.createBitmap(bounds.width().roundToInt(), bounds.height().roundToInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val p = paperLuma.coerceIn(0, 255)
        canvas.drawColor(Color.rgb(p, p, p)) // corners blend with the paper
        canvas.drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        out
    } catch (_: OutOfMemoryError) {
        null
    }
}
