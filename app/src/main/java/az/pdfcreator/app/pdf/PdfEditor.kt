package az.pdfcreator.app.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Page-level PDF operations without re-encoding the content (lossless):
 * reorder, delete, rotate, merge several PDFs, extract a selection. Also renders pages to JPG.
 */
object PdfEditor {

    /** A page in the editor: page [index] of source file [src], plus extra clockwise [rotation]. */
    data class PageRef(val id: Long, val src: Int, val index: Int, val rotation: Int = 0)

    class PasswordNeeded : Exception()
    class WrongPassword : Exception()

    /**
     * Copies a picked PDF into [workDir] and returns a file that Android's PdfRenderer can open.
     * Encrypted PDFs need [password]; they are decrypted to a private copy.
     */
    fun open(context: Context, uri: Uri, workDir: File, password: String?): Pair<File, Int> {
        workDir.mkdirs()
        val copy = File(workDir, "src_${System.nanoTime()}.pdf")
        context.contentResolver.openInputStream(uri)?.use { input -> copy.outputStream().use { input.copyTo(it) } }
            ?: error("Cannot read PDF")
        val count = pageCount(copy)
        if (count != null) return copy to count

        // PdfRenderer refused it: most likely encrypted. Decrypt with PdfBox.
        val doc = try {
            if (password == null) PDDocument.load(copy, MemoryUsageSetting.setupTempFileOnly())
            else PDDocument.load(copy, password, MemoryUsageSetting.setupTempFileOnly())
        } catch (e: InvalidPasswordException) {
            copy.delete()
            throw if (password == null) PasswordNeeded() else WrongPassword()
        }
        val plain = File(workDir, "dec_${System.nanoTime()}.pdf")
        doc.use {
            it.isAllSecurityToBeRemoved = true
            it.save(plain)
        }
        copy.delete()
        val n = pageCount(plain) ?: error("Unsupported PDF")
        return plain to n
    }

    fun pageCount(file: File): Int? = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd -> PdfRenderer(pfd).use { it.pageCount } }
    }.getOrNull()

    /** Writes [pages] (in order, with rotations) from [sources] into [out]. */
    fun save(sources: List<File>, pages: List<PageRef>, out: File) {
        val docs = sources.map { PDDocument.load(it, MemoryUsageSetting.setupTempFileOnly()) }
        try {
            PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { result ->
                for (p in pages) {
                    val page = docs[p.src].getPage(p.index)
                    val imported = result.importPage(page)
                    imported.rotation = ((page.rotation + p.rotation) % 360 + 360) % 360
                }
                result.save(out)
            }
        } finally {
            docs.forEach { runCatching { it.close() } }
        }
    }

    /** Renders one page to a bitmap whose longest side is about [maxSide] px (rotation applied). */
    fun render(file: File, index: Int, rotation: Int, maxSide: Int): Bitmap? = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                renderer.openPage(index).use { page ->
                    val scale = maxSide.toFloat() / max(page.width, page.height)
                    val w = (page.width * scale).roundToInt().coerceAtLeast(1)
                    val h = (page.height * scale).roundToInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    if (rotation % 360 == 0) bmp
                    else Bitmap.createBitmap(bmp, 0, 0, w, h, Matrix().apply { postRotate(rotation.toFloat()) }, true)
                        .also { if (it !== bmp) bmp.recycle() }
                }
            }
        }
    }.getOrNull()

    /**
     * Saves each page as a JPG in Pictures/PDF Creator (visible in the gallery).
     * @return uris of the saved images, for sharing.
     */
    fun exportJpg(
        context: Context,
        sources: List<File>,
        pages: List<PageRef>,
        baseName: String,
        onProgress: (Int, Int) -> Unit,
    ): List<Uri> {
        val result = ArrayList<Uri>()
        pages.forEachIndexed { i, p ->
            onProgress(i, pages.size)
            // ~200 dpi for A4, capped for memory.
            val bmp = render(sources[p.src], p.index, p.rotation, 2400) ?: return@forEachIndexed
            try {
                val name = "${baseName}_${i + 1}.jpg"
                saveImage(context, bmp, name)?.let { result.add(it) }
            } finally {
                bmp.recycle()
            }
        }
        onProgress(pages.size, pages.size)
        return result
    }

    private fun saveImage(context: Context, bmp: Bitmap, name: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PDF Creator")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            uri
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir, "PDF Creator").apply { mkdirs() }
            val f = File(dir, name)
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
        }
    }
}
