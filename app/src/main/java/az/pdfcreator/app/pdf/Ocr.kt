package az.pdfcreator.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** On-device text recognition (ML Kit, Latin script incl. Azerbaijani). Works offline. */
object Ocr {

    data class Line(val text: String, val box: Rect)

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    @Volatile private var fixer: AzOcrFixer? = null

    /** Azerbaijani correction (dictionary loaded once from assets, ~0.3 s). */
    fun fixer(context: Context): AzOcrFixer? = fixer ?: synchronized(this) {
        fixer ?: runCatching {
            val am = context.applicationContext.assets
            AzOcrFixer(
                AzSpell(
                    java.util.zip.GZIPInputStream(am.open("spell/az.dic.gz")),
                    am.open("spell/az.aff"),
                    am.open("spell/extra.txt"),
                ),
            )
        }.getOrNull().also { fixer = it }
    }

    /** OCR lines → corrected lines (Azerbaijani pages only; others untouched). */
    fun correct(context: Context, lines: List<Line>): List<Line> {
        val f = fixer(context) ?: return lines
        if (!AzOcrFixer.looksAzerbaijani(AzOcrFixer.normalize(lines.joinToString(" ") { it.text }))) return lines
        return lines.map { it.copy(text = f.fix(it.text)) }
    }

    /** Text lines with their boxes in [bitmap] pixels, or null if recognition is unavailable. */
    suspend fun lines(bitmap: Bitmap): List<Line>? = runCatching {
        val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        text.textBlocks.flatMap { block -> block.lines.mapNotNull { l -> l.boundingBox?.let { Line(l.text, it) } } }
    }.getOrNull()

    /** All text of an image (blocks separated by blank lines). */
    suspend fun text(context: Context, uri: Uri, rotation: Int = 0, maxSide: Int = 2200): String? {
        val loader = ImageLoader(context.contentResolver)
        val info = loader.info(uri, rotation) ?: return null
        val bmp = loader.decode(uri, info, maxSide, maxSide) ?: return null
        return try {
            runCatching {
                val result = recognizer.process(InputImage.fromBitmap(bmp, 0)).await()
                val lines = result.textBlocks.flatMap { b -> b.lines.mapNotNull { l -> l.boundingBox?.let { Line(l.text, it) } } }
                val fixed = correct(context, lines)
                ReadingOrder.join(fixed.map { ReadingOrder.Box(it.text, it.box.left, it.box.top, it.box.right, it.box.bottom) }).trim()
            }.getOrNull()
        } finally {
            bmp.recycle()
        }
    }

    /**
     * A file name from the document's first meaningful line, e.g. "Məxfilik_müqaviləsi_07.11.2026".
     * Returns null when nothing usable is found.
     */
    fun suggestName(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val line = text.lineSequence()
            .map { it.trim() }
            .firstOrNull { l -> l.count { it.isLetter() } >= 4 && l.length <= 80 } ?: return null
        val words = line.replace(Regex("[^\\p{L}\\p{N} \\-]"), " ").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(5)
        if (words.isEmpty()) return null
        // "MƏXFİLİK MÜQAVİLƏSİ" -> "Məxfilik müqaviləsi" (sentence case reads better as a file name).
        val az = Locale.forLanguageTag("az")
        val joined = words.joinToString("_").lowercase(az).replaceFirstChar { it.titlecase(az) }.take(50)
        return joined + "_" + SimpleDateFormat("dd.MM.yyyy", Locale.US).format(Date())
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
}
