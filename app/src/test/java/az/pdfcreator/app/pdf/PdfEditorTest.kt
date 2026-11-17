package az.pdfcreator.app.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import az.pdfcreator.app.model.PageItem
import az.pdfcreator.app.model.PdfOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PdfEditorTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before fun init() = PDFBoxResourceLoader.init(ctx)

    private fun pdf(name: String, pages: Int, color: Int, password: String = ""): File {
        val items = (0 until pages).map { i ->
            val bmp = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            val f = File(ctx.cacheDir, "$name$i.jpg").also { o -> o.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) } }
            PageItem(i.toLong(), Uri.fromFile(f))
        }
        val out = File(ctx.cacheDir, "$name.pdf")
        runBlocking { PdfGenerator(ctx).generate(items, PdfOptions(password = password), out) { _, _ -> } }
        return out
    }

    @Test fun mergeReorderRotateDelete() {
        val a = pdf("a", 3, Color.RED)
        val b = pdf("b", 2, Color.BLUE)
        val pages = listOf(
            PdfEditor.PageRef(1, 1, 1),            // b page 2 first
            PdfEditor.PageRef(2, 0, 0, 90),        // a page 1, rotated
            PdfEditor.PageRef(3, 0, 2),            // a page 3 (a page 2 deleted)
        )
        val out = File(ctx.cacheDir, "merged.pdf")
        PdfEditor.save(listOf(a, b), pages, out)
        PDDocument.load(out).use { doc ->
            assertEquals(3, doc.numberOfPages)
            assertEquals(0, doc.getPage(0).rotation)
            assertEquals(90, doc.getPage(1).rotation)
        }
        // Lossless: no re-encoding, so the result is about the size of the parts.
        assertTrue(out.length() < (a.length() + b.length()) * 1.2)
    }

    @Test fun decryptsWithPassword() {
        val locked = pdf("locked", 2, Color.GREEN, password = "1234")
        val doc = PDDocument.load(locked, "1234")
        doc.isAllSecurityToBeRemoved = true
        val plain = File(ctx.cacheDir, "plain.pdf")
        doc.save(plain); doc.close()
        PDDocument.load(plain).use { assertEquals(2, it.numberOfPages); assertTrue(!it.isEncrypted) }
    }

    @Test fun smartName() {
        val name = Ocr.suggestName("MƏXFİLİK MÜQAVİLƏSİ\n93.12.2006-cı il\nBu Məxfilik müqaviləsi")!!
        assertTrue(name, name.startsWith("Məxfilik_müqaviləsi_"))
        assertEquals(null, Ocr.suggestName("12 34\n--"))
    }
}
