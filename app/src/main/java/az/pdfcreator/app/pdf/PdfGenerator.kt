package az.pdfcreator.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import az.pdfcreator.app.model.ColorMode
import az.pdfcreator.app.model.Layout
import az.pdfcreator.app.model.SignaturePages
import az.pdfcreator.app.model.SignaturePos
import az.pdfcreator.app.model.OrientationOption
import az.pdfcreator.app.model.PageItem
import az.pdfcreator.app.model.PageSizeOption
import az.pdfcreator.app.model.PdfOptions
import az.pdfcreator.app.model.ScaleMode
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class PdfGenerator(context: Context) {

    private val appContext = context.applicationContext

    data class Result(val pages: Int, val skipped: Int)

    class NothingToWriteException : Exception("No readable images")

    private val loader = ImageLoader(context.contentResolver)

    /** Result of [generateWithLimit]. */
    data class LimitedResult(val result: Result, val overLimit: Boolean, val passes: Int)

    /**
     * Like [generate], but if [PdfOptions.maxSizeKb] is set and the file is too big, lowers the
     * resolution / JPEG quality and tries again (up to 6 passes) until it fits.
     */
    suspend fun generateWithLimit(
        items: List<PageItem>,
        options: PdfOptions,
        out: File,
        signatureFile: File?,
        onProgress: suspend (done: Int, total: Int, pass: Int) -> Unit,
    ): LimitedResult {
        val limit = options.maxSizeKb * 1024L
        var dpi = options.quality.dpi
        var jpeg = options.quality.jpeg
        var pass = 1
        val first = generate(items, options, out, signatureFile, dpi, jpeg) { d, t -> onProgress(d, t, pass) }
        if (limit <= 0 || out.length() <= limit) return LimitedResult(first, false, pass)

        // Phase 1: jump down (size grows roughly with dpi^2) until it fits.
        var overDpi = dpi
        var overJpeg = jpeg
        var fitRes: Result? = null
        val backup = File(out.parentFile, out.name + ".fit")
        try {
            var last = first
            while (fitRes == null) {
                if (pass >= MAX_PASSES || (dpi <= MIN_DPI && jpeg <= MIN_JPEG)) return LimitedResult(last, true, pass)
                val ratio = limit.toDouble() / out.length()
                overDpi = dpi
                overJpeg = jpeg
                dpi = (dpi * kotlin.math.sqrt(ratio) * 0.93).toInt().coerceIn(MIN_DPI, maxOf(MIN_DPI, dpi - 5))
                jpeg = (jpeg - 8).coerceAtLeast(MIN_JPEG)
                pass++
                last = generate(items, options, out, signatureFile, dpi, jpeg) { d, t -> onProgress(d, t, pass) }
                if (out.length() <= limit) {
                    fitRes = last
                    out.copyTo(backup, overwrite = true)
                }
            }
            // Phase 2: binary search upwards for the best quality that still fits.
            var fitDpi = dpi
            var fitJpeg = jpeg
            var bestRes: Result = fitRes!!
            var lastFits = true
            while (pass < MAX_PASSES && overDpi - fitDpi >= 10 && backup.length() < limit * 0.75) {
                val midDpi = (fitDpi + overDpi) / 2
                val midJpeg = (fitJpeg + overJpeg) / 2
                pass++
                val res = generate(items, options, out, signatureFile, midDpi, midJpeg) { d, t -> onProgress(d, t, pass) }
                if (out.length() <= limit) {
                    fitDpi = midDpi; fitJpeg = midJpeg; bestRes = res; lastFits = true
                    out.copyTo(backup, overwrite = true)
                } else {
                    overDpi = midDpi; overJpeg = midJpeg; lastFits = false
                }
            }
            if (!lastFits) backup.copyTo(out, overwrite = true)
            return LimitedResult(bestRes, false, pass)
        } finally {
            backup.delete()
        }
    }

    /**
     * Writes [items] to [out]. Only one image is held in memory at a time.
     * [onProgress] is called after each image with (done, total).
     */
    suspend fun generate(
        items: List<PageItem>,
        options: PdfOptions,
        out: File,
        signatureFile: File? = null,
        dpi: Int = options.quality.dpi,
        jpeg: Int = options.quality.jpeg,
        onProgress: suspend (done: Int, total: Int) -> Unit,
    ): Result {
        out.parentFile?.mkdirs()
        var skipped = 0
        var done = 0
        val total = items.size
        val idCard = options.layout == Layout.ID_CARD
        val groups = if (idCard) listOf(items.take(2)) else items.chunked(options.imagesPerPage)
        val pagesTotal = groups.size

        FileOutputStream(out).use { fos ->
            val writer = SimplePdfWriter(fos, options.password.ifEmpty { null })
            val watermark = options.watermark.trim().takeIf { it.isNotEmpty() }?.let { addWatermark(writer, it) }
            val signature = if (options.signature && signatureFile != null && signatureFile.exists()) {
                runCatching { addSignature(writer, signatureFile, options) }
                    .onFailure { android.util.Log.w("PdfGenerator", "signature failed", it) }.getOrNull()
            } else null

            var pending: PendingPage? = null
            groups.forEachIndexed { _, group ->
                currentCoroutineContext().ensureActive()
                val infos = group.map { loader.info(it.uri, it.rotation) }
                val (pageW, pageH) = if (idCard) PageSizeOption.A4.widthPt to PageSizeOption.A4.heightPt else pageSize(options, infos.firstOrNull())
                val margin = options.margin.pt
                val content = RectF(margin, margin, pageW - margin, pageH - margin)
                val cells = if (idCard) idCardCells(pageW, pageH, group.size, options.idCardScale)
                else cells(content, group.size.coerceAtLeast(1), options.imagesPerPage, pageW >= pageH)
                val pageOptions = if (idCard) options.copy(scaleMode = ScaleMode.FILL) else options

                val draws = ArrayList<SimplePdfWriter.ImageDraw>()
                val hidden = if (options.searchable) ArrayList<SimplePdfWriter.HiddenText>() else null
                group.forEachIndexed { index, item ->
                    currentCoroutineContext().ensureActive()
                    val info = infos[index]
                    val draw = if (info == null) null else runCatching {
                        placeImage(writer, item, info, cells[index], pageOptions, dpi, jpeg, hidden)
                    }.getOrNull()
                    if (draw == null) skipped++ else draws.add(draw)
                    done++
                    onProgress(done, total)
                }
                if (draws.isEmpty()) return@forEachIndexed

                if (watermark != null) draws.add(watermarkDraw(watermark, pageW, pageH))
                if (signature != null && options.signaturePages == SignaturePages.ALL) {
                    draws.add(signatureDraw(signature, pageW, pageH, margin, options.signaturePos))
                }
                // Pages are written one step late, so the "last page" signature lands on the
                // last page that was really produced (unreadable images are skipped).
                pending?.let { writePage(writer, it, options, idCard, pagesTotal) }
                pending = PendingPage(pageW, pageH, margin, draws, hidden.orEmpty())
            }
            pending?.let { page ->
                if (signature != null && options.signaturePages == SignaturePages.LAST) {
                    page.draws.add(signatureDraw(signature, page.w, page.h, page.margin, options.signaturePos))
                }
                writePage(writer, page, options, idCard, pagesTotal)
            }

            if (writer.pageCount == 0) throw NothingToWriteException()
            writer.finish()
            return Result(writer.pageCount, skipped)
        }
    }

    private class PendingPage(
        val w: Float,
        val h: Float,
        val margin: Float,
        val draws: MutableList<SimplePdfWriter.ImageDraw>,
        val hidden: List<SimplePdfWriter.HiddenText>,
    )

    private fun writePage(writer: SimplePdfWriter, p: PendingPage, options: PdfOptions, idCard: Boolean, pagesTotal: Int) {
        val texts = if (options.pageNumbers && !idCard) {
            val y = if (p.margin >= 20f) p.margin / 2f - 3f else 10f
            listOf(SimplePdfWriter.TextDraw("${writer.pageCount + 1} / $pagesTotal", p.w / 2f, y, 9f))
        } else emptyList()
        writer.addPage(p.w, p.h, p.draws, texts, p.hidden)
    }

    /** Two ID-card sized cells (85.6 x 54 mm) stacked in the upper part of an A4 page. */
    private fun idCardCells(pageW: Float, pageH: Float, count: Int, scale: Float): List<RectF> {
        val mm = 72f / 25.4f
        val w = 85.6f * mm * scale
        val h = 53.98f * mm * scale
        val gap = 14f * mm
        val left = (pageW - w) / 2f
        val top = pageH - 40f * mm // 40 mm from the top edge, like a photocopy
        val front = RectF(left, top - h, left + w, top)
        val back = RectF(left, top - 2 * h - gap, left + w, top - h - gap)
        return listOf(front, back).take(count.coerceAtLeast(1))
    }

    private companion object {
        const val MIN_DPI = 60
        const val MIN_JPEG = 35
        const val MAX_PASSES = 7
    }

    // ------------------------------------------------------------------ layout

    private fun pageSize(options: PdfOptions, firstInfo: ImageLoader.Info?): Pair<Float, Float> {
        val margin = options.margin.pt
        if (options.pageSize == PageSizeOption.FIT_IMAGE && options.imagesPerPage == 1 && firstInfo != null) {
            // Page follows the image's shape; long side matches A4's long side.
            val longSide = 842f - 2 * margin
            val aspect = firstInfo.width.toFloat() / firstInfo.height
            val (w, h) = if (aspect >= 1f) longSide to longSide / aspect else longSide * aspect to longSide
            return (w + 2 * margin) to (h + 2 * margin)
        }
        val base = if (options.pageSize == PageSizeOption.FIT_IMAGE) PageSizeOption.A4 else options.pageSize
        val short = min(base.widthPt, base.heightPt)
        val long = max(base.widthPt, base.heightPt)
        val landscape = when (options.orientation) {
            OrientationOption.PORTRAIT -> false
            OrientationOption.LANDSCAPE -> true
            OrientationOption.AUTO -> options.imagesPerPage == 1 && firstInfo != null && firstInfo.width > firstInfo.height
        }
        return if (landscape) long to short else short to long
    }

    /** Cells in PDF coordinates (origin bottom-left), ordered top-to-bottom, left-to-right. */
    private fun cells(content: RectF, count: Int, perPage: Int, landscape: Boolean): List<RectF> {
        if (perPage == 1) return listOf(content)
        val gap = 8f
        val result = ArrayList<RectF>()
        if (perPage == 2) {
            if (landscape) {
                val w = (content.width() - gap) / 2f
                result += RectF(content.left, content.top, content.left + w, content.bottom)
                result += RectF(content.left + w + gap, content.top, content.right, content.bottom)
            } else {
                val h = (content.height() - gap) / 2f
                // Top cell first (higher y in PDF space).
                result += RectF(content.left, content.bottom - h, content.right, content.bottom)
                result += RectF(content.left, content.top, content.right, content.top + h)
            }
        } else {
            val w = (content.width() - gap) / 2f
            val h = (content.height() - gap) / 2f
            for (row in 0 until 2) for (col in 0 until 2) {
                val left = content.left + col * (w + gap)
                val topY = content.bottom - row * (h + gap)
                result += RectF(left, topY - h, left + w, topY)
            }
        }
        return result
    }

    // RectF here is used with top < bottom meaning "lower y" < "higher y" in PDF space.
    private suspend fun placeImage(
        writer: SimplePdfWriter,
        item: PageItem,
        info: ImageLoader.Info,
        cell: RectF,
        options: PdfOptions,
        dpi: Int,
        jpegQuality: Int,
        hidden: MutableList<SimplePdfWriter.HiddenText>? = null,
    ): SimplePdfWriter.ImageDraw? {
        val cellW = cell.width()
        val cellH = cell.height()
        if (cellW <= 1f || cellH <= 1f) return null
        val imgW = info.width.toFloat()
        val imgH = info.height.toFloat()

        val drawW: Float
        val drawH: Float
        if (options.scaleMode == ScaleMode.FIT) {
            val s = min(cellW / imgW, cellH / imgH)
            drawW = imgW * s; drawH = imgH * s
        } else {
            drawW = cellW; drawH = cellH
        }
        // Target pixels for the visible area, capped to avoid memory spikes.
        var targetW = (drawW / 72f * dpi).roundToInt().coerceAtLeast(1)
        var targetH = (drawH / 72f * dpi).roundToInt().coerceAtLeast(1)
        val cap = 4200f / max(targetW, targetH)
        if (cap < 1f) { targetW = (targetW * cap).roundToInt(); targetH = (targetH * cap).roundToInt() }

        // How many source pixels we need so that the visible part is >= target.
        val needW: Int
        val needH: Int
        if (options.scaleMode == ScaleMode.FIT) {
            needW = targetW; needH = targetH
        } else {
            val s = max(targetW / imgW, targetH / imgH)
            needW = (imgW * s).roundToInt(); needH = (imgH * s).roundToInt()
        }

        val src = loader.decode(item.uri, info, needW, needH) ?: return null
        try {
            // Source rectangle (crop for FILL) in decoded pixels.
            val srcRect = if (options.scaleMode == ScaleMode.FILL) {
                val cellAspect = cellW / cellH
                val srcAspect = src.width.toFloat() / src.height
                if (srcAspect > cellAspect) {
                    val w = (src.height * cellAspect).roundToInt()
                    val x = (src.width - w) / 2
                    Rect(x, 0, x + w, src.height)
                } else {
                    val h = (src.width / cellAspect).roundToInt()
                    val y = (src.height - h) / 2
                    Rect(0, y, src.width, y + h)
                }
            } else Rect(0, 0, src.width, src.height)

            // Never upscale: output is at most the cropped source size.
            val outW = min(targetW, srcRect.width()).coerceAtLeast(1)
            val outH = min(targetH, srcRect.height()).coerceAtLeast(1)
            val finalBmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(finalBmp)
                canvas.drawColor(Color.WHITE) // flattens PNG transparency
                val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                colorFilter(options.colorMode)?.let { paint.colorFilter = it }
                canvas.drawBitmap(src, srcRect, Rect(0, 0, outW, outH), paint)

                val bos = ByteArrayOutputStream(outW * outH / 4)
                finalBmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality, bos)
                val ref = writer.addJpegImage(bos.toByteArray(), outW, outH)
                val x = cell.left + (cellW - drawW) / 2f
                val y = cell.top + (cellH - drawH) / 2f
                if (hidden != null) {
                    // OCR on exactly the pixels we embed, then map each line box to page points.
                    Ocr.lines(finalBmp)?.let { Ocr.correct(appContext, it) }?.forEach { line ->
                        val b = line.box
                        val lx = x + b.left.toFloat() / outW * drawW
                        val lw = b.width().toFloat() / outW * drawW
                        val lh = b.height().toFloat() / outH * drawH
                        val ly = y + drawH - b.bottom.toFloat() / outH * drawH
                        hidden.add(SimplePdfWriter.HiddenText(line.text, lx, ly, lw, lh))
                    }
                }
                return SimplePdfWriter.ImageDraw.rect(ref, x, y, drawW, drawH)
            } finally {
                finalBmp.recycle()
            }
        } finally {
            src.recycle()
        }
    }

    private fun colorFilter(mode: ColorMode): ColorMatrixColorFilter? = when (mode) {
        ColorMode.ORIGINAL -> null
        ColorMode.GRAYSCALE -> ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        ColorMode.DOCUMENT -> {
            // Grayscale + strong contrast and a little brightness: paper becomes white, ink darker.
            val gray = ColorMatrix().apply { setSaturation(0f) }
            val c = 1.6f
            val t = -0.3f * 255f + 25f
            val contrast = ColorMatrix(
                floatArrayOf(
                    c, 0f, 0f, 0f, t,
                    0f, c, 0f, 0f, t,
                    0f, 0f, c, 0f, t,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
            gray.postConcat(contrast)
            ColorMatrixColorFilter(gray)
        }
    }

    // --------------------------------------------------------------- watermark

    private class Watermark(val ref: SimplePdfWriter.ImageRef, val aspect: Float)

    private fun addWatermark(writer: SimplePdfWriter, text: String): Watermark {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 160f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.BLACK
        }
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        val pad = 20
        val w = (bounds.width() + pad * 2).coerceIn(1, 4000)
        val h = (bounds.height() + pad * 2).coerceIn(1, 1000)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawText(text, (pad - bounds.left).toFloat(), (pad - bounds.top).toFloat(), paint)
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        bmp.recycle()
        val alpha = ByteArray(w * h) { i -> (((pixels[i] ushr 24) and 0xFF) * 0.22f).toInt().toByte() }
        val ref = writer.addAlphaMaskedColorImage(110, 110, 110, alpha, w, h)
        return Watermark(ref, w.toFloat() / h)
    }

    private fun addSignature(writer: SimplePdfWriter, file: File, options: PdfOptions): Watermark? {
        val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        bmp.recycle()
        val alpha = ByteArray(w * h) { i -> ((pixels[i] ushr 24) and 0xFF).toByte() }
        val ink = options.signatureInk
        val ref = writer.addAlphaMaskedColorImage(ink.r, ink.g, ink.b, alpha, w, h)
        return Watermark(ref, w.toFloat() / h)
    }

    /** Signature near the bottom of the page, about 28% of the page width. */
    private fun signatureDraw(sig: Watermark, pageW: Float, pageH: Float, margin: Float, pos: SignaturePos): SimplePdfWriter.ImageDraw {
        var w = pageW * 0.28f
        var h = w / sig.aspect
        val maxH = pageH * 0.09f
        if (h > maxH) { h = maxH; w = h * sig.aspect }
        val side = maxOf(margin, 28f) + 8f
        val x = when (pos) {
            SignaturePos.RIGHT -> pageW - side - w
            SignaturePos.CENTER -> (pageW - w) / 2f
            SignaturePos.LEFT -> side
        }
        val y = maxOf(margin, 20f) + 14f
        return SimplePdfWriter.ImageDraw.rect(sig.ref, x, y, w, h)
    }

    private fun watermarkDraw(wm: Watermark, pageW: Float, pageH: Float): SimplePdfWriter.ImageDraw {
        val angle = atan2(pageH, pageW)
        val diagonal = kotlin.math.sqrt(pageW * pageW + pageH * pageH)
        var w = diagonal * 0.6f
        var h = w / wm.aspect
        val maxH = min(pageW, pageH) * 0.2f
        if (h > maxH) { h = maxH; w = h * wm.aspect }
        val c = cos(angle); val s = sin(angle)
        val a = w * c; val b = w * s; val cc = -h * s; val d = h * c
        val e = pageW / 2f - (a + cc) / 2f
        val f = pageH / 2f - (b + d) / 2f
        return SimplePdfWriter.ImageDraw(wm.ref, a, b, cc, d, e, f)
    }
}
