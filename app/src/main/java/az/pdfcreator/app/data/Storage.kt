package az.pdfcreator.app.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import az.pdfcreator.app.model.HistoryEntry
import az.pdfcreator.app.model.PdfOptions
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Small key-value settings: last used PDF options and theme. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun loadOptions(): PdfOptions = sp.getString(KEY_OPTIONS, null)
        ?.let { runCatching { PdfOptions.fromJson(JSONObject(it)) }.getOrNull() }
        ?: PdfOptions()

    /** File name, password and per-document choices (signature, ID-card layout) are never remembered. */
    fun saveOptions(o: PdfOptions) {
        sp.edit().putString(
            KEY_OPTIONS,
            o.copy(fileName = "", password = "", signature = false, layout = az.pdfcreator.app.model.Layout.NORMAL).toJson().toString(),
        ).apply()
    }

    var themeMode: Int
        get() = sp.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) = sp.edit().putInt(KEY_THEME, value).apply()

    /** True once the user has finished (or skipped) the welcome pages. */
    var onboardingDone: Boolean
        get() = sp.getBoolean(KEY_ONBOARDING, false)
        set(value) = sp.edit().putBoolean(KEY_ONBOARDING, value).apply()

    /** Straighten scanned pages automatically (default on). */
    var autoStraighten: Boolean
        get() = sp.getBoolean(KEY_STRAIGHTEN, true)
        set(value) = sp.edit().putBoolean(KEY_STRAIGHTEN, value).apply()

    /** Restore A4 proportions of scanned pages that the scanner squashed (default on). */
    var snapA4: Boolean
        get() = sp.getBoolean(KEY_SNAP_A4, true)
        set(value) = sp.edit().putBoolean(KEY_SNAP_A4, value).apply()

    /** Folder picked by the user (SAF tree uri), or null for Download/PDF Creator. */
    var saveTreeUri: String?
        get() = sp.getString(KEY_TREE, null)
        set(value) = sp.edit().putString(KEY_TREE, value).apply()

    var saveTreeLabel: String?
        get() = sp.getString(KEY_TREE_LABEL, null)
        set(value) = sp.edit().putString(KEY_TREE_LABEL, value).apply()

    private companion object {
        const val KEY_SNAP_A4 = "snap_a4"
        const val KEY_TREE = "save_tree_uri"
        const val KEY_TREE_LABEL = "save_tree_label"
        const val KEY_STRAIGHTEN = "auto_straighten"
        const val KEY_ONBOARDING = "onboarding_done"
        const val KEY_OPTIONS = "pdf_options"
        const val KEY_THEME = "theme_mode"
    }
}

/** List of created PDFs, stored as JSON in app-private storage. */
class HistoryRepository(private val context: Context) {
    private val file = File(context.filesDir, "history.json")
    private val lock = Any()

    fun load(): List<HistoryEntry> = synchronized(lock) {
        if (!file.exists()) return emptyList()
        val arr = runCatching { JSONArray(file.readText()) }.getOrElse { return emptyList() }
        val all = (0 until arr.length()).map { HistoryEntry.fromJson(arr.getJSONObject(it)) }
        val alive = all.filter { exists(it) }
        if (alive.size != all.size) write(alive)
        alive.sortedByDescending { it.createdAt }
    }

    fun add(entry: HistoryEntry) = synchronized(lock) {
        val current = if (file.exists()) runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { HistoryEntry.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList()) else emptyList()
        write(listOf(entry) + current)
    }

    fun remove(entry: HistoryEntry, deleteFile: Boolean): Boolean = synchronized(lock) {
        var ok = true
        if (deleteFile) ok = OutputSaver.delete(context, entry)
        val rest = load().filterNot { it.uri == entry.uri }
        write(rest)
        ok
    }

    private fun write(list: List<HistoryEntry>) {
        val arr = JSONArray()
        list.take(500).forEach { arr.put(it.toJson()) }
        file.writeText(arr.toString())
    }

    private fun exists(e: HistoryEntry): Boolean {
        e.filePath?.let { return File(it).exists() }
        val uri = Uri.parse(e.uri)
        val column = if (DocumentsContract.isDocumentUri(context, uri)) DocumentsContract.Document.COLUMN_DOCUMENT_ID else MediaStore.MediaColumns._ID
        return runCatching {
            context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { it.moveToFirst() } ?: false
        }.getOrDefault(false)
    }
}

/** Copies a finished PDF to Downloads/PDF Creator (or app storage as fallback). */
object OutputSaver {
    const val FOLDER = "PDF Creator"

    const val DEFAULT_LABEL = "Download/$FOLDER"

    data class Saved(val uri: Uri, val name: String, val filePath: String?, val size: Long, val location: String = DEFAULT_LABEL)

    fun authority(context: Context) = "${context.packageName}.fileprovider"

    /** Saves to the user's chosen folder if set; falls back to Download/PDF Creator if that fails. */
    fun save(context: Context, source: File, requestedName: String): Saved {
        val name = sanitize(requestedName).let { if (it.endsWith(".pdf", true)) it else "$it.pdf" }
        val prefs = Prefs(context)
        prefs.saveTreeUri?.let { tree ->
            runCatching { return saveToTree(context, source, name, Uri.parse(tree), prefs.saveTreeLabel ?: "") }
        }
        return saveDefault(context, source, name)
    }

    private fun saveToTree(context: Context, source: File, name: String, tree: Uri, label: String): Saved {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val doc = DocumentsContract.createDocument(resolver, parent, "application/pdf", name) ?: error("createDocument failed")
        try {
            resolver.openOutputStream(doc)?.use { out -> source.inputStream().use { it.copyTo(out, 256 * 1024) } }
                ?: error("Cannot open output")
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, doc) }
            throw e
        }
        val finalName = resolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: name
        return Saved(doc, finalName, null, source.length(), label)
    }

    /** Human-readable name for a picked folder, e.g. "Documents/Scans" or "SD/Scans". */
    fun labelForTree(context: Context, tree: Uri): String {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrDefault("")
        if (tree.authority == "com.android.externalstorage.documents" && docId.contains(':')) {
            val volume = docId.substringBefore(':')
            val path = docId.substringAfter(':').ifEmpty { "/" }
            return if (volume == "primary") path else "SD/$path"
        }
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
        return runCatching {
            context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: docId
    }

    private fun saveDefault(context: Context, source: File, name: String): Saved {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveMediaStore(context, source, name)
        } else {
            runCatching { saveLegacyPublic(context, source, name) }
                .getOrElse { saveAppStorage(context, source, name) }
        }
    }

    fun sanitize(name: String): String {
        val cleaned = name.trim().replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(120)
        return cleaned.ifBlank { "PDF" }
    }

    private fun saveMediaStore(context: Context, source: File, name: String): Saved {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: error("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out, 256 * 1024) } }
                ?: error("Cannot open output")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        // MediaStore may have renamed it, e.g. "file (1).pdf".
        val finalName = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: name
        return Saved(uri, finalName, null, source.length())
    }

    @Suppress("DEPRECATION")
    private fun saveLegacyPublic(context: Context, source: File, name: String): Saved {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
        if (!dir.exists() && !dir.mkdirs()) error("Cannot create folder")
        val target = uniqueFile(dir, name)
        source.copyTo(target, overwrite = false)
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf("application/pdf"), null)
        return Saved(FileProvider.getUriForFile(context, authority(context), target), target.name, target.absolutePath, target.length())
    }

    private fun saveAppStorage(context: Context, source: File, name: String): Saved {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "PDF")
        dir.mkdirs()
        val target = uniqueFile(dir, name)
        source.copyTo(target, overwrite = false)
        return Saved(FileProvider.getUriForFile(context, authority(context), target), target.name, target.absolutePath, target.length())
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.removeSuffix(".pdf")
        var i = 1
        while (f.exists()) f = File(dir, "$base (${i++}).pdf")
        return f
    }

    fun delete(context: Context, entry: HistoryEntry): Boolean = runCatching {
        val uri = Uri.parse(entry.uri)
        when {
            entry.filePath != null -> File(entry.filePath).delete()
            DocumentsContract.isDocumentUri(context, uri) -> DocumentsContract.deleteDocument(context.contentResolver, uri)
            else -> context.contentResolver.delete(uri, null, null) > 0
        }
    }.getOrDefault(false)
}
