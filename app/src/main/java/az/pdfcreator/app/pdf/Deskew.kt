package az.pdfcreator.app.pdf

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Finds how much a document photo is tilted, using the text lines themselves:
 * when the page is rotated so that lines are perfectly horizontal, the dark pixels
 * pile up in few rows (a "spiky" horizontal projection). We search the angle that
 * maximises that spikiness. Pure Kotlin, ~50 ms on a phone for an 1000 px preview.
 */
object Deskew {

    data class Result(
        /** Rotate by this many degrees (clockwise positive, like Matrix.postRotate); 0 = already straight. */
        val correction: Float,
        /** Paper colour (RGB luminance 0-255) to fill the corners created by rotation. */
        val paperLuma: Int,
    )

    /**
     * @param luma luminance 0..255 of a downscaled image (row-major, [w] x [h]).
     * @return null when the image does not look like a text document.
     */
    fun estimate(luma: IntArray, w: Int, h: Int, maxAngle: Float = 15f): Result? {
        if (w < 50 || h < 50) return null
        val threshold = otsu(luma)

        // Ignore a thin border: scanner crops often leave shadows at the edges.
        val mx = w / 40
        val my = h / 40
        var dark = 0
        var paperSum = 0L
        var paperCount = 0
        for (y in my until h - my) for (x in mx until w - mx) {
            val v = luma[y * w + x]
            if (v < threshold) dark++ else { paperSum += v; paperCount++ }
        }
        val area = (w - 2 * mx) * (h - 2 * my)
        val darkRatio = dark.toFloat() / area
        // Text pages: a few percent of pixels are ink. Photos/blank pages are skipped.
        if (darkRatio < 0.003f || darkRatio > 0.35f) return null
        val paper = if (paperCount > 0) (paperSum / paperCount).toInt() else 255

        // Collect ink points (subsampled to keep it fast).
        val step = maxOf(1, dark / 60_000)
        val xs = IntArray(dark / step + 1)
        val ys = IntArray(dark / step + 1)
        var n = 0
        var k = 0
        for (y in my until h - my) for (x in mx until w - mx) {
            if (luma[y * w + x] < threshold) {
                if (k++ % step == 0 && n < xs.size) { xs[n] = x; ys[n] = y; n++ }
            }
        }
        val cx = w / 2f
        val cy = h / 2f
        val bins = IntArray((w + h) * 2)
        fun score(deg: Float): Long {
            val r = Math.toRadians(deg.toDouble())
            val s = sin(r).toFloat()
            val c = cos(r).toFloat()
            java.util.Arrays.fill(bins, 0)
            val off = bins.size / 2
            for (i in 0 until n) {
                val dx = xs[i] - cx
                val dy = ys[i] - cy
                // Row of the point after rotating the image by `deg` (clockwise in screen coordinates).
                val yr = (dx * s + dy * c).roundToInt() + off
                if (yr in bins.indices) bins[yr]++
            }
            var sum = 0L
            for (b in bins) sum += b.toLong() * b
            return sum
        }

        // Coarse search, then refine.
        var best = 0f
        var bestScore = Long.MIN_VALUE
        var a = -maxAngle
        while (a <= maxAngle + 1e-3f) {
            val sc = score(a)
            if (sc > bestScore) { bestScore = sc; best = a }
            a += 0.5f
        }
        val coarse = best
        a = coarse - 0.5f
        while (a <= coarse + 0.5f + 1e-3f) {
            val sc = score(a)
            if (sc > bestScore) { bestScore = sc; best = a }
            a += 0.05f
        }

        // Must be clearly better than doing nothing, and not at the search edge (= no real lines found).
        val zero = score(0f)
        val tilted = abs(best) >= 0.15f && abs(abs(best) - maxAngle) >= 0.3f && bestScore >= zero * 1.03
        return Result(if (tilted) best else 0f, paper)
    }

    private fun otsu(luma: IntArray): Int {
        val hist = IntArray(256)
        for (v in luma) hist[v.coerceIn(0, 255)]++
        val total = luma.size.toDouble()
        var sumAll = 0.0
        for (i in 0 until 256) sumAll += i * hist[i].toDouble()
        var sumB = 0.0
        var wB = 0.0
        var maxVar = -1.0
        var thr = 128
        for (t in 0 until 256) {
            wB += hist[t]
            if (wB == 0.0) continue
            val wF = total - wB
            if (wF == 0.0) break
            sumB += t * hist[t].toDouble()
            val mB = sumB / wB
            val mF = (sumAll - sumB) / wF
            val between = wB * wF * (mB - mF) * (mB - mF)
            if (between > maxVar) { maxVar = between; thr = t }
        }
        return thr
    }
}
