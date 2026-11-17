package az.pdfcreator.app.pdf

import java.io.BufferedOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.zip.Deflater

/**
 * Minimal streaming PDF 1.4 writer.
 *
 * - Embeds JPEG data directly (DCTDecode), so the PDF stays small (Android's PdfDocument
 *   stores pixels losslessly, which makes photo PDFs huge).
 * - Writes each page as soon as it is added: memory use stays constant no matter how many pages.
 * - Supports Flate images with an alpha mask (used for the watermark), Helvetica text
 *   (page numbers) and the Standard Security Handler, RC4 128-bit (V2/R3), for passwords.
 *
 * Pure JVM code (no Android imports) so it can be unit tested on a desktop JVM.
 */
class SimplePdfWriter(
    output: OutputStream,
    userPassword: String? = null,
) : AutoCloseable {

    class ImageRef internal constructor(internal val objNum: Int, val name: String)

    /** Draws [image] with the PDF transformation matrix [a b c d e f] (points). */
    data class ImageDraw(
        val image: ImageRef,
        val a: Float, val b: Float, val c: Float, val d: Float, val e: Float, val f: Float,
    ) {
        companion object {
            /** Axis aligned placement: lower-left corner (x, y), size w x h, in points. */
            fun rect(image: ImageRef, x: Float, y: Float, w: Float, h: Float) =
                ImageDraw(image, w, 0f, 0f, h, x, y)
        }
    }

    data class TextDraw(
        val text: String,
        val x: Float,
        val y: Float,
        val size: Float,
        val gray: Float = 0.35f,
        val centered: Boolean = true,
    )

    /**
     * Invisible, searchable text (e.g. from OCR), stretched to fill the box
     * [x],[y] (lower-left, points) of width [w] and height [h].
     */
    data class HiddenText(val text: String, val x: Float, val y: Float, val w: Float, val h: Float)

    /**
     * Type3 font with empty glyphs whose only job is to carry Unicode (via ToUnicode),
     * so any language (ə, ş, ğ, Cyrillic…) is searchable and copyable. Up to 255 chars per font.
     */
    private class UnicodeFont(val objNum: Int, val name: String) {
        val codes = LinkedHashMap<Char, Int>()
        val full get() = codes.size >= 255
    }

    private val unicodeFonts = ArrayList<UnicodeFont>()
    private val charFont = HashMap<Char, UnicodeFont>()

    private fun fontFor(c: Char): UnicodeFont {
        charFont[c]?.let { return it }
        val font = unicodeFonts.lastOrNull()?.takeIf { !it.full }
            ?: UnicodeFont(nextObj++, "U${unicodeFonts.size + 1}").also { unicodeFonts.add(it) }
        font.codes[c] = font.codes.size + 1
        charFont[c] = font
        return font
    }

    private val out = CountingStream(BufferedOutputStream(output, 64 * 1024))
    private val offsets = HashMap<Int, Long>()
    private val pageObjNums = ArrayList<Int>()
    private var nextObj = 1
    private var imageCounter = 0
    private var finished = false

    private val catalogObj = nextObj++
    private val pagesObj = nextObj++
    private val fontObj = nextObj++

    private val fileId: ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }
    private val security: Rc4Security? =
        if (userPassword.isNullOrEmpty()) null else Rc4Security(userPassword, fileId)

    init {
        out.writeAscii("%PDF-1.4\n")
        // Binary comment so transfer tools treat the file as binary.
        out.write(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))
        writeObject(fontObj, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
    }

    val pageCount: Int get() = pageObjNums.size

    /** Adds a baseline JPEG (as produced by Android's Bitmap.compress). */
    fun addJpegImage(jpeg: ByteArray, width: Int, height: Int): ImageRef {
        val num = nextObj++
        val dict = "<< /Type /XObject /Subtype /Image /Width $width /Height $height " +
            "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode"
        writeStreamObject(num, dict, jpeg)
        return ImageRef(num, "Im${++imageCounter}")
    }

    /**
     * Adds a single-colour image whose shape comes from an 8-bit [alpha] mask (width*height bytes).
     * Used for the watermark: any Unicode text can be rendered to a mask on the device.
     */
    fun addAlphaMaskedColorImage(r: Int, g: Int, b: Int, alpha: ByteArray, width: Int, height: Int): ImageRef {
        require(alpha.size == width * height) { "alpha size mismatch" }
        val maskNum = nextObj++
        writeStreamObject(
            maskNum,
            "<< /Type /XObject /Subtype /Image /Width $width /Height $height " +
                "/ColorSpace /DeviceGray /BitsPerComponent 8 /Filter /FlateDecode",
            deflate(alpha),
        )
        val rgb = ByteArray(width * height * 3)
        var i = 0
        while (i < rgb.size) {
            rgb[i] = r.toByte(); rgb[i + 1] = g.toByte(); rgb[i + 2] = b.toByte(); i += 3
        }
        val num = nextObj++
        writeStreamObject(
            num,
            "<< /Type /XObject /Subtype /Image /Width $width /Height $height " +
                "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode /SMask $maskNum 0 R",
            deflate(rgb),
        )
        return ImageRef(num, "Im${++imageCounter}")
    }

    fun addPage(
        widthPt: Float,
        heightPt: Float,
        images: List<ImageDraw>,
        texts: List<TextDraw> = emptyList(),
        hidden: List<HiddenText> = emptyList(),
    ) {
        check(!finished) { "writer already finished" }
        val content = StringBuilder()
        val pageFonts = LinkedHashSet<UnicodeFont>()
        for (d in images) {
            content.append("q ")
                .append(n(d.a)).append(' ').append(n(d.b)).append(' ')
                .append(n(d.c)).append(' ').append(n(d.d)).append(' ')
                .append(n(d.e)).append(' ').append(n(d.f)).append(" cm /")
                .append(d.image.name).append(" Do Q\n")
        }
        for (t in texts) {
            val safe = t.text.filter { it.code in 32..126 }
                .replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
            // Helvetica digits are 0.556 em wide; good enough to centre short labels.
            val width = safe.length * 0.556f * t.size
            val x = if (t.centered) t.x - width / 2f else t.x
            content.append("BT ").append(n(t.gray)).append(" g /F1 ").append(n(t.size))
                .append(" Tf ").append(n(x)).append(' ').append(n(t.y))
                .append(" Td (").append(safe).append(") Tj ET\n")
        }
        for (t in hidden) {
            val chars = t.text.filter { !it.isSurrogate() && !it.isISOControl() }
            if (chars.isBlank() || t.w <= 0f || t.h <= 0f) continue
            val size = t.h * 0.85f
            // Every glyph is 500/1000 em wide; horizontal scaling makes the run match the box.
            val natural = chars.length * 0.5f * size
            val tz = (t.w / natural * 100f).coerceIn(1f, 2000f)
            content.append("BT 3 Tr ").append(n(tz)).append(" Tz ")
                .append(n(t.x)).append(' ').append(n(t.y + t.h * 0.18f)).append(" Td ")
            var current: UnicodeFont? = null
            val hex = StringBuilder()
            fun flush() {
                val f = current ?: return
                if (hex.isEmpty()) return
                content.append('/').append(f.name).append(' ').append(n(size)).append(" Tf <").append(hex).append("> Tj ")
                hex.setLength(0)
            }
            for (c in chars) {
                val f = fontFor(c)
                if (f !== current) { flush(); current = f; pageFonts.add(f) }
                hex.append(String.format(Locale.US, "%02X", f.codes.getValue(c)))
            }
            flush()
            content.append("ET\n")
        }
        val contentNum = nextObj++
        writeStreamObject(contentNum, "<<", content.toString().toByteArray(Charsets.ISO_8859_1))

        val xobjects = images.map { it.image }.distinctBy { it.objNum }
            .joinToString(" ") { "/${it.name} ${it.objNum} 0 R" }
        val pageNum = nextObj++
        writeObject(
            pageNum,
            "<< /Type /Page /Parent $pagesObj 0 R /MediaBox [0 0 ${n(widthPt)} ${n(heightPt)}] " +
                "/Resources << /ProcSet [/PDF /Text /ImageC] /Font << /F1 $fontObj 0 R" +
                pageFonts.joinToString("") { " /${it.name} ${it.objNum} 0 R" } + " >> " +
                "/XObject << $xobjects >> >> /Contents $contentNum 0 R >>",
        )
        pageObjNums.add(pageNum)
    }

    /** Writes page tree, catalog, xref and trailer. The underlying stream is flushed, not closed. */
    fun finish() {
        if (finished) return
        check(pageObjNums.isNotEmpty()) { "PDF must contain at least one page" }
        finished = true
        writeUnicodeFonts()
        writeObject(
            pagesObj,
            "<< /Type /Pages /Count ${pageObjNums.size} /Kids [" +
                pageObjNums.joinToString(" ") { "$it 0 R" } + "] >>",
        )
        writeObject(catalogObj, "<< /Type /Catalog /Pages $pagesObj 0 R >>")

        val infoObj = nextObj++
        writeObject(infoObj, "<< /Producer ${pdfString(infoObj, "PDF Creator for Android")} >>")

        var encryptObj = 0
        security?.let { sec ->
            encryptObj = nextObj++
            writeObject(
                encryptObj,
                "<< /Filter /Standard /V 2 /R 3 /Length 128 /P ${sec.permissions} " +
                    "/O <${hex(sec.oValue)}> /U <${hex(sec.uValue)}> >>",
            )
        }

        val xrefOffset = out.count
        val size = nextObj
        val sb = StringBuilder()
        sb.append("xref\n0 ").append(size).append('\n')
        sb.append("0000000000 65535 f\r\n")
        for (i in 1 until size) {
            val off = offsets[i] ?: error("object $i was never written")
            sb.append(String.format(Locale.US, "%010d 00000 n\r\n", off))
        }
        val id = hex(fileId)
        sb.append("trailer\n<< /Size ").append(size)
            .append(" /Root ").append(catalogObj).append(" 0 R /Info ").append(infoObj).append(" 0 R")
        if (encryptObj != 0) sb.append(" /Encrypt ").append(encryptObj).append(" 0 R")
        sb.append(" /ID [<").append(id).append("> <").append(id).append(">] >>\n")
        sb.append("startxref\n").append(xrefOffset).append("\n%%EOF\n")
        out.writeAscii(sb.toString())
        out.flush()
    }

    override fun close() {
        out.flush()
        out.close()
    }

    // ---------------------------------------------------------------- internals

    private fun writeUnicodeFonts() {
        if (unicodeFonts.isEmpty()) return
        // One shared, empty glyph program (advance width 500).
        val glyphNum = nextObj++
        writeStreamObject(glyphNum, "<<", "500 0 d0".toByteArray(Charsets.ISO_8859_1))
        for (font in unicodeFonts) {
            val count = font.codes.size
            val cmap = StringBuilder()
            cmap.append("/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n")
                .append("/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n")
                .append("/CMapName /Adobe-Identity-UCS def\n/CMapType 2 def\n")
                .append("1 begincodespacerange\n<00> <FF>\nendcodespacerange\n")
            font.codes.entries.chunked(100).forEach { chunk ->
                cmap.append(chunk.size).append(" beginbfchar\n")
                chunk.forEach { (c, code) ->
                    cmap.append(String.format(Locale.US, "<%02X> <%04X>\n", code, c.code))
                }
                cmap.append("endbfchar\n")
            }
            cmap.append("endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n")
            val cmapNum = nextObj++
            writeStreamObject(cmapNum, "<<", cmap.toString().toByteArray(Charsets.ISO_8859_1))

            val names = (1..count).joinToString(" ") { "/g$it" }
            val procs = (1..count).joinToString(" ") { "/g$it $glyphNum 0 R" }
            val widths = (1..count).joinToString(" ") { "500" }
            writeObject(
                font.objNum,
                "<< /Type /Font /Subtype /Type3 /FontBBox [0 0 500 700] /FontMatrix [0.001 0 0 0.001 0 0] " +
                    "/CharProcs << $procs >> /Encoding << /Type /Encoding /Differences [1 $names] >> " +
                    "/FirstChar 1 /LastChar $count /Widths [$widths] /ToUnicode $cmapNum 0 R /Resources << >> >>",
            )
        }
    }

    /** Writes a non-stream object. Bodies must not contain literal strings (use [pdfString]). */
    private fun writeObject(num: Int, body: String) {
        offsets[num] = out.count
        out.writeAscii("$num 0 obj\n")
        out.writeAscii(body)
        out.writeAscii("\nendobj\n")
    }

    /** [dictStart] is an unterminated dictionary ("<< /Type ..."); /Length and ">>" are appended. */
    private fun writeStreamObject(num: Int, dictStart: String, data: ByteArray) {
        val payload = security?.encrypt(num, data) ?: data
        offsets[num] = out.count
        out.writeAscii("$num 0 obj\n")
        out.writeAscii(dictStart)
        out.writeAscii(" /Length ${payload.size} >>\nstream\n")
        out.write(payload)
        out.writeAscii("\nendstream\nendobj\n")
    }

    private fun pdfString(objNum: Int, value: String): String {
        val bytes = value.toByteArray(Charsets.ISO_8859_1)
        val sec = security ?: return "<${hex(bytes)}>"
        return "<${hex(sec.encrypt(objNum, bytes))}>"
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(data)
        deflater.finish()
        val buf = java.io.ByteArrayOutputStream(maxOf(1024, data.size / 4))
        val chunk = ByteArray(64 * 1024)
        while (!deflater.finished()) {
            val n = deflater.deflate(chunk)
            buf.write(chunk, 0, n)
        }
        deflater.end()
        return buf.toByteArray()
    }

    private class CountingStream(private val target: OutputStream) : OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) { target.write(b); count++ }
        override fun write(b: ByteArray, off: Int, len: Int) { target.write(b, off, len); count += len }
        override fun flush() = target.flush()
        override fun close() = target.close()
        fun writeAscii(s: String) = write(s.toByteArray(Charsets.ISO_8859_1))
    }

    /** Standard security handler, revision 3, 128-bit RC4 (PDF 1.4, ISO 32000-1 §7.6.3). */
    private class Rc4Security(userPassword: String, private val fileId: ByteArray) {
        /** Printing, copying, etc. all allowed; only opening requires the password. */
        val permissions: Int = -4
        val oValue: ByteArray
        val uValue: ByteArray
        private val key: ByteArray

        init {
            val user = pad(userPassword)
            // Owner password: random, so nobody can strip the protection with a known owner key.
            val ownerPwd = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val owner = pad(ownerPwd)

            // Algorithm 3: O value
            var h = md5(owner)
            repeat(50) { h = md5(h) }
            val oKey = h.copyOf(16)
            var o = rc4(oKey, user)
            for (i in 1..19) o = rc4(xorKey(oKey, i), o)
            oValue = o

            // Algorithm 2: encryption key
            val md = MessageDigest.getInstance("MD5")
            md.update(user)
            md.update(oValue)
            md.update(byteArrayOf(permissions.toByte(), (permissions shr 8).toByte(), (permissions shr 16).toByte(), (permissions shr 24).toByte()))
            md.update(fileId)
            var k = md.digest()
            repeat(50) { k = md5(k.copyOf(16)) }
            key = k.copyOf(16)

            // Algorithm 5: U value
            val md2 = MessageDigest.getInstance("MD5")
            md2.update(PADDING)
            md2.update(fileId)
            var u = rc4(key, md2.digest())
            for (i in 1..19) u = rc4(xorKey(key, i), u)
            uValue = u + ByteArray(16) // 16 arbitrary padding bytes
        }

        fun encrypt(objNum: Int, data: ByteArray): ByteArray {
            val md = MessageDigest.getInstance("MD5")
            md.update(key)
            md.update(byteArrayOf(objNum.toByte(), (objNum shr 8).toByte(), (objNum shr 16).toByte(), 0, 0))
            val objKey = md.digest().copyOf(minOf(key.size + 5, 16))
            return rc4(objKey, data)
        }

        companion object {
            private val PADDING = byteArrayOf(
                0x28, 0xBF.toByte(), 0x4E, 0x5E, 0x4E, 0x75, 0x8A.toByte(), 0x41,
                0x64, 0x00, 0x4E, 0x56, 0xFF.toByte(), 0xFA.toByte(), 0x01, 0x08,
                0x2E, 0x2E, 0x00, 0xB6.toByte(), 0xD0.toByte(), 0x68, 0x3E, 0x80.toByte(),
                0x2F, 0x0C, 0xA9.toByte(), 0xFE.toByte(), 0x64, 0x53, 0x69, 0x7A,
            )

            private fun pad(password: String): ByteArray {
                // PDF 1.4 passwords are PDFDocEncoding; keep Latin-1 and map anything else to '?'.
                val raw = password.map { if (it.code in 0..255) it.code.toByte() else '?'.code.toByte() }.toByteArray()
                return pad(raw)
            }

            private fun pad(raw: ByteArray): ByteArray {
                val p = ByteArray(32)
                val n = minOf(32, raw.size)
                System.arraycopy(raw, 0, p, 0, n)
                System.arraycopy(PADDING, 0, p, n, 32 - n)
                return p
            }

            private fun md5(b: ByteArray): ByteArray = MessageDigest.getInstance("MD5").digest(b)

            private fun xorKey(k: ByteArray, i: Int) = ByteArray(k.size) { (k[it].toInt() xor i).toByte() }

            fun rc4(key: ByteArray, data: ByteArray): ByteArray {
                val s = IntArray(256) { it }
                var j = 0
                for (i in 0 until 256) {
                    j = (j + s[i] + (key[i % key.size].toInt() and 0xFF)) and 0xFF
                    val t = s[i]; s[i] = s[j]; s[j] = t
                }
                val result = ByteArray(data.size)
                var i = 0
                j = 0
                for (x in data.indices) {
                    i = (i + 1) and 0xFF
                    j = (j + s[i]) and 0xFF
                    val t = s[i]; s[i] = s[j]; s[j] = t
                    result[x] = (data[x].toInt() xor s[(s[i] + s[j]) and 0xFF]).toByte()
                }
                return result
            }
        }
    }

    private companion object {
        fun n(v: Float): String {
            if (v == v.toLong().toFloat()) return v.toLong().toString()
            return String.format(Locale.US, "%.3f", v).trimEnd('0').trimEnd('.')
        }

        fun hex(b: ByteArray): String {
            val sb = StringBuilder(b.size * 2)
            for (x in b) sb.append(String.format(Locale.US, "%02X", x.toInt() and 0xFF))
            return sb.toString()
        }
    }
}
