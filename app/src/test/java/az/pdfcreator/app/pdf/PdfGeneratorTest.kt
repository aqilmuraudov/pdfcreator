package az.pdfcreator.app.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import az.pdfcreator.app.model.ColorMode
import az.pdfcreator.app.model.MarginOption
import az.pdfcreator.app.model.PageItem
import az.pdfcreator.app.model.PageSizeOption
import az.pdfcreator.app.model.PdfOptions
import az.pdfcreator.app.model.QualityOption
import az.pdfcreator.app.model.ScaleMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Runs the real generator with real Android bitmaps (Robolectric native graphics). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PdfGeneratorTest {

    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val outDir = File(System.getProperty("pdftest.out") ?: "build/pdf-test-output").apply { mkdirs() }

    private fun image(name: String, w: Int, h: Int, bg: Int, png: Boolean = false): Uri {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(bg)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = h / 6f }
        c.drawText(name, w * 0.08f, h * 0.55f, p)
        // Arrow pointing up, to check rotation.
        p.strokeWidth = w / 40f
        c.drawLine(w * 0.85f, h * 0.8f, w * 0.85f, h * 0.2f, p)
        c.drawLine(w * 0.85f, h * 0.2f, w * 0.78f, h * 0.3f, p)
        c.drawLine(w * 0.85f, h * 0.2f, w * 0.92f, h * 0.3f, p)
        val f = File(ctx.cacheDir, "$name.${if (png) "png" else "jpg"}")
        f.outputStream().use { bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 90, it) }
        return Uri.fromFile(f)
    }

    private fun items(): List<PageItem> = listOf(
        PageItem(1, image("Portrait", 1200, 1800, Color.rgb(25, 118, 210))),
        PageItem(2, image("Landscape", 1800, 1000, Color.rgb(56, 142, 60))),
        PageItem(3, image("Rotated", 1200, 800, Color.rgb(123, 31, 162)), rotation = 90),
        PageItem(4, image("PNG", 900, 900, Color.rgb(230, 81, 0), png = true)),
        PageItem(5, Uri.fromFile(File(ctx.cacheDir, "missing.jpg"))), // unreadable -> skipped
    )

    private fun run(name: String, options: PdfOptions): PdfGenerator.Result {
        val out = File(outDir, "$name.pdf")
        val res = runBlocking { PdfGenerator(ctx).generate(items(), options, out) { _, _ -> } }
        assertTrue(out.length() > 1000)
        assertTrue(String(out.readBytes().copyOfRange(0, 8), Charsets.ISO_8859_1).startsWith("%PDF-1.4"))
        return res
    }

    private fun signatureFile(): File {
        val bmp = Bitmap.createBitmap(600, 220, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 9f; style = Paint.Style.STROKE }
        val path = android.graphics.Path().apply {
            moveTo(20f, 160f); cubicTo(120f, 10f, 160f, 210f, 260f, 90f); cubicTo(330f, 20f, 380f, 200f, 470f, 120f); lineTo(580f, 140f)
        }
        c.drawPath(path, p)
        return File(ctx.filesDir, "signature.png").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    @Test fun signatureOnLastPage() {
        val out = File(outDir, "signature.pdf")
        val res = runBlocking {
            PdfGenerator(ctx).generate(items(), PdfOptions(signature = true, signaturePos = az.pdfcreator.app.model.SignaturePos.RIGHT), out, signatureFile()) { _, _ -> }
        }
        assertEquals(4, res.pages)
    }

    @Test fun idCardPage() {
        val front = image("FRONT", 1012, 638, Color.rgb(25, 118, 210))
        val back = image("BACK", 1012, 638, Color.rgb(56, 142, 60))
        val out = File(outDir, "idcard.pdf")
        val res = runBlocking {
            PdfGenerator(ctx).generate(
                listOf(PageItem(1, front), PageItem(2, back)),
                PdfOptions(layout = az.pdfcreator.app.model.Layout.ID_CARD), out,
            ) { _, _ -> }
        }
        assertEquals(1, res.pages)
    }

    @Test fun sizeLimitShrinksFile() {
        // Noisy images compress badly: a good stress test for the limit.
        val noisy = (0 until 6).map { i ->
            val bmp = Bitmap.createBitmap(2400, 3200, Bitmap.Config.ARGB_8888)
            val rnd = java.util.Random(i.toLong())
            val px = IntArray(bmp.width * bmp.height) { Color.rgb(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256)) }
            bmp.setPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            val f = File(ctx.cacheDir, "noise$i.jpg"); f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            PageItem(i.toLong(), Uri.fromFile(f))
        }
        val out = File(outDir, "limited.pdf")
        val unlimited = File(outDir, "unlimited.pdf")
        runBlocking { PdfGenerator(ctx).generate(noisy, PdfOptions(quality = QualityOption.HIGH), unlimited) { _, _ -> } }
        val limit = 1024
        val res = runBlocking {
            PdfGenerator(ctx).generateWithLimit(noisy, PdfOptions(quality = QualityOption.HIGH, maxSizeKb = limit), out, null) { _, _, _ -> }
        }
        println("unlimited=${unlimited.length() / 1024} KB limited=${out.length() / 1024} KB passes=${res.passes} over=${res.overLimit}")
        assertTrue(unlimited.length() > limit * 1024L)
        assertTrue(res.overLimit || out.length() <= limit * 1024L)
        assertTrue(!res.overLimit)
    }

    @Test fun defaultA4() {
        val r = run("default", PdfOptions(pageNumbers = true))
        assertEquals(4, r.pages)
        assertEquals(1, r.skipped)
    }

    @Test fun fourPerPageDocumentWatermarkPassword() {
        val r = run(
            "grid4",
            PdfOptions(
                imagesPerPage = 4, colorMode = ColorMode.DOCUMENT, watermark = "MƏXFİ",
                password = "1234", margin = MarginOption.MEDIUM, pageNumbers = true,
            ),
        )
        assertEquals(1, r.pages)
    }

    @Test fun fitImageFillGray() {
        run("fitimage", PdfOptions(pageSize = PageSizeOption.FIT_IMAGE, margin = MarginOption.NONE, quality = QualityOption.LOW))
        val r = run("fill2", PdfOptions(imagesPerPage = 2, scaleMode = ScaleMode.FILL, colorMode = ColorMode.GRAYSCALE))
        assertEquals(2, r.pages)
    }
}
