package az.pdfcreator.app.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class SimplePdfWriterTest {

    // The writer embeds JPEG bytes verbatim, so placeholder bytes are enough for structure tests.
    @Suppress("UNUSED_PARAMETER")
    private fun jpeg(w: Int, h: Int): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + ByteArray(64) { it.toByte() } + byteArrayOf(0xFF.toByte(), 0xD9.toByte())

    private fun build(password: String?, pages: Int): String {
        val bos = ByteArrayOutputStream()
        val w = SimplePdfWriter(bos, password)
        repeat(pages) {
            val img = w.addJpegImage(jpeg(200, 100), 200, 100)
            w.addPage(595f, 842f, listOf(SimplePdfWriter.ImageDraw.rect(img, 10f, 10f, 200f, 100f)),
                listOf(SimplePdfWriter.TextDraw("${it + 1}", 297f, 10f, 9f)))
        }
        w.finish()
        return String(bos.toByteArray(), Charsets.ISO_8859_1)
    }

    @Test fun writesValidStructure() {
        val pdf = build(null, 3)
        assertTrue(pdf.startsWith("%PDF-1.4"))
        assertTrue(pdf.trimEnd().endsWith("%%EOF"))
        assertTrue(pdf.contains("/Count 3"))
        assertEquals(3, Regex("/DCTDecode").findAll(pdf).count())
        // xref offset must point at "xref"
        val start = Regex("startxref\\n(\\d+)").find(pdf)!!.groupValues[1].toInt()
        assertTrue(pdf.startsWith("xref", start))
    }

    @Test fun encryptsWithPassword() {
        val pdf = build("secret", 1)
        assertTrue(pdf.contains("/Encrypt"))
        assertTrue(pdf.contains("/V 2 /R 3 /Length 128"))
    }
}
