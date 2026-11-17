package az.pdfcreator.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import az.pdfcreator.app.data.HistoryRepository
import az.pdfcreator.app.data.Prefs
import az.pdfcreator.app.model.HistoryEntry
import az.pdfcreator.app.model.PageItem
import az.pdfcreator.app.model.PdfJob
import az.pdfcreator.app.model.PdfOptions
import az.pdfcreator.app.work.PdfWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

sealed interface GenState {
    data object Idle : GenState
    data class Running(val done: Int, val total: Int, val pass: Int = 1) : GenState
    data class Success(
        val uri: Uri,
        val name: String,
        val pages: Int,
        val size: Long,
        val skipped: Int,
        val protected: Boolean,
        val location: String = az.pdfcreator.app.data.OutputSaver.DEFAULT_LABEL,
        val overLimit: Boolean = false,
        val limitKb: Int = 0,
    ) : GenState
    data class Failed(val error: String) : GenState
}

/** Progress of importing pages from an existing PDF. */
data class ImportState(val done: Int, val total: Int)

sealed interface UiEvent {
    data class PdfImportFailed(val locked: Boolean) : UiEvent
    data class Straightened(val changed: Boolean) : UiEvent
    data class Added(val count: Int) : UiEvent
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val historyRepo = HistoryRepository(app)
    private val workManager = WorkManager.getInstance(app)
    private val idGen = AtomicLong(1)
    private val importDir = File(app.filesDir, "imported")

    private val _items = MutableStateFlow<List<PageItem>>(emptyList())
    val items: StateFlow<List<PageItem>> = _items.asStateFlow()

    private val _options = MutableStateFlow(prefs.loadOptions())
    val options: StateFlow<PdfOptions> = _options.asStateFlow()

    private val _gen = MutableStateFlow<GenState>(GenState.Idle)
    val gen: StateFlow<GenState> = _gen.asStateFlow()

    private val _import = MutableStateFlow<ImportState?>(null)
    val importState: StateFlow<ImportState?> = _import.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val _events = MutableStateFlow<UiEvent?>(null)
    val events: StateFlow<UiEvent?> = _events.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.themeMode)
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    private val _onboardingDone = MutableStateFlow(prefs.onboardingDone)
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    private val _autoStraighten = MutableStateFlow(prefs.autoStraighten)
    val autoStraighten: StateFlow<Boolean> = _autoStraighten.asStateFlow()

    private val _snapA4 = MutableStateFlow(prefs.snapA4)
    val snapA4: StateFlow<Boolean> = _snapA4.asStateFlow()

    fun setSnapA4(on: Boolean) {
        prefs.snapA4 = on
        _snapA4.value = on
    }

    /** Custom save folder label, or null for the default Download/PDF Creator. */
    private val _saveFolder = MutableStateFlow(if (prefs.saveTreeUri != null) prefs.saveTreeLabel else null)
    val saveFolder: StateFlow<String?> = _saveFolder.asStateFlow()

    private var observeJob: Job? = null

    init {
        // Pages imported from PDFs in an earlier session are no longer referenced.
        viewModelScope.launch(Dispatchers.IO) { importDir.listFiles()?.forEach { it.delete() } }
        refreshHistory()
    }

    // ------------------------------------------------------------------ items

    fun addUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val new = uris.map { PageItem(idGen.getAndIncrement(), it) }
        _items.update { it + new }
        _events.value = UiEvent.Added(new.size)
    }

    fun rotate(id: Long) = _items.update { list ->
        list.map { if (it.id == id) it.copy(rotation = (it.rotation + 90) % 360) else it }
    }

    fun rotateAll() = _items.update { list -> list.map { it.copy(rotation = (it.rotation + 90) % 360) } }

    fun remove(id: Long) = _items.update { list ->
        list.filterNot { it.id == id }.also { cleanupImported(list.filter { i -> i.id == id }) }
    }

    fun move(fromIndex: Int, toIndex: Int) = _items.update { list ->
        if (fromIndex !in list.indices || toIndex !in list.indices) list
        else list.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }

    /** Reorder by item ids (the grid also contains header items, so indices don't match). */
    fun moveByKey(fromId: Long, toId: Long) = _items.update { list ->
        val f = list.indexOfFirst { it.id == fromId }
        val t = list.indexOfFirst { it.id == toId }
        if (f < 0 || t < 0) list else list.toMutableList().apply { add(t, removeAt(f)) }
    }

    fun reverse() = _items.update { it.reversed() }

    fun clear() {
        val old = _items.value
        _items.value = emptyList()
        cleanupImported(old)
    }

    fun consumeEvent() { _events.value = null }

    private fun cleanupImported(removed: List<PageItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            removed.forEach { item ->
                val path = item.uri.path ?: return@forEach
                if (item.uri.scheme == "file" && path.startsWith(importDir.absolutePath)) File(path).delete()
            }
        }
    }

    /**
     * Pages from the document scanner: straightened automatically (if enabled) before they
     * appear in the editor, so the user sees them already level.
     */
    fun addScanned(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val level = _autoStraighten.value
        val snap = _snapA4.value
        if (!level && !snap) { addUris(uris); return }
        viewModelScope.launch {
            val app = getApplication<Application>()
            val fixed = withContext(Dispatchers.Default) {
                val s = az.pdfcreator.app.pdf.Straightener(app, importDir)
                uris.mapIndexed { i, uri ->
                    _import.value = ImportState(i, uris.size)
                    runCatching { s.straighten(uri, level = level, snapA4 = snap) }.getOrNull() ?: uri
                }
            }
            _import.value = null
            addUris(fixed)
        }
    }

    /** Manual "straighten" for any image (also gallery photos of documents). */
    fun straighten(id: Long) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            val app = getApplication<Application>()
            _import.value = ImportState(0, 1)
            val newUri = withContext(Dispatchers.Default) {
                runCatching { az.pdfcreator.app.pdf.Straightener(app, importDir).straighten(item.uri, item.rotation, level = true, snapA4 = _snapA4.value) }.getOrNull()
            }
            _import.value = null
            if (newUri != null) {
                // The user's rotation is baked into the new file.
                _items.update { list -> list.map { if (it.id == id) it.copy(uri = newUri, rotation = 0) else it } }
                cleanupImported(listOf(item))
            }
            _events.value = UiEvent.Straightened(newUri != null)
        }
    }

    /** Called with a folder from the system picker; keeps access across restarts. */
    fun setSaveFolder(tree: Uri) {
        val app = getApplication<Application>()
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { app.contentResolver.takePersistableUriPermission(tree, flags) }
        // Drop access to the previous folder.
        prefs.saveTreeUri?.let { old ->
            if (old != tree.toString()) runCatching { app.contentResolver.releasePersistableUriPermission(Uri.parse(old), flags) }
        }
        val label = az.pdfcreator.app.data.OutputSaver.labelForTree(app, tree)
        prefs.saveTreeUri = tree.toString()
        prefs.saveTreeLabel = label
        _saveFolder.value = label
    }

    fun resetSaveFolder() {
        val app = getApplication<Application>()
        prefs.saveTreeUri?.let { old ->
            runCatching {
                app.contentResolver.releasePersistableUriPermission(
                    Uri.parse(old),
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        prefs.saveTreeUri = null
        prefs.saveTreeLabel = null
        _saveFolder.value = null
    }

    fun setAutoStraighten(on: Boolean) {
        prefs.autoStraighten = on
        _autoStraighten.value = on
    }

    /** Renders every page of a PDF to a JPEG and adds it, so existing PDFs can be merged. */
    fun importPdf(uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val added = ArrayList<Uri>()
            val ok = withContext(Dispatchers.IO) {
                try {
                    importDir.mkdirs()
                    app.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            val count = renderer.pageCount
                            for (i in 0 until count) {
                                _import.value = ImportState(i, count)
                                renderer.openPage(i).use { page ->
                                    // ~150 dpi, capped for memory.
                                    val scale = minOf(150f / 72f, 2400f / maxOf(page.width, page.height))
                                    val w = (page.width * scale).toInt().coerceAtLeast(1)
                                    val h = (page.height * scale).toInt().coerceAtLeast(1)
                                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                    bmp.eraseColor(Color.WHITE)
                                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    val f = File(importDir, "${UUID.randomUUID()}.jpg")
                                    FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                                    bmp.recycle()
                                    added += Uri.fromFile(f)
                                }
                            }
                        }
                    } ?: return@withContext false
                    true
                } catch (e: SecurityException) {
                    _events.value = UiEvent.PdfImportFailed(locked = true); null
                } catch (e: Exception) {
                    false
                } catch (e: OutOfMemoryError) {
                    false
                }
            }
            _import.value = null
            if (added.isNotEmpty()) addUris(added)
            if (ok == false) _events.value = UiEvent.PdfImportFailed(locked = false)
        }
    }

    // ---------------------------------------------------------------- options

    fun updateOptions(transform: (PdfOptions) -> PdfOptions) {
        _options.update(transform)
        prefs.saveOptions(_options.value)
    }

    fun defaultFileName(): String =
        "PDF_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    // ------------------------------------------------------------- generation

    private var lastLimitKb = 0

    fun startGeneration() {
        val opts = _options.value.let { if (it.fileName.isBlank()) it.copy(fileName = _suggestedName.value ?: defaultFileName()) else it }
        enqueue(_items.value, opts)
    }

    private fun enqueue(list: List<PageItem>, opts: PdfOptions) {
        if (list.isEmpty() || _gen.value is GenState.Running) return
        val app = getApplication<Application>()
        lastLimitKb = opts.maxSizeKb
        _gen.value = GenState.Running(0, list.size)
        viewModelScope.launch {
            val jobFile = withContext(Dispatchers.IO) {
                File(app.filesDir, "jobs").apply { mkdirs() }.let { dir ->
                    File(dir, "${UUID.randomUUID()}.json").apply { writeText(PdfJob(list, opts).toJson()) }
                }
            }
            val request = OneTimeWorkRequestBuilder<PdfWorker>()
                .setInputData(workDataOf(PdfWorker.KEY_JOB to jobFile.absolutePath))
                .build()
            workManager.enqueueUniqueWork(PdfWorker.UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
            observe(request.id)
        }
    }

    // ------------------------------------------------------------- ID card

    private val _idFront = MutableStateFlow<Uri?>(null)
    val idFront: StateFlow<Uri?> = _idFront.asStateFlow()
    private val _idBack = MutableStateFlow<Uri?>(null)
    val idBack: StateFlow<Uri?> = _idBack.asStateFlow()

    fun setIdSide(front: Boolean, uri: Uri?) {
        if (front) _idFront.value = uri else _idBack.value = uri
    }

    /** One A4 page with the card's front and back at real (or enlarged) size. */
    fun startIdCard(scale: Float, colorMode: az.pdfcreator.app.model.ColorMode) {
        val sides = listOfNotNull(_idFront.value, _idBack.value)
        if (sides.isEmpty()) return
        val base = _options.value
        val opts = base.copy(
            fileName = idCardFileName(),
            layout = az.pdfcreator.app.model.Layout.ID_CARD,
            idCardScale = scale,
            colorMode = colorMode,
            quality = az.pdfcreator.app.model.QualityOption.HIGH,
            watermark = "", password = "", signature = false, pageNumbers = false, maxSizeKb = 0,
        )
        enqueue(sides.mapIndexed { i, u -> PageItem(i.toLong(), u) }, opts)
    }

    fun clearIdCard() { _idFront.value = null; _idBack.value = null }

    private fun idCardFileName(): String {
        val app = getApplication<Application>()
        return app.getString(az.pdfcreator.app.R.string.id_card_file_prefix) + "_" +
            SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
    }

    // ------------------------------------------------------------------ OCR

    /** File name suggested from the first page's text (shown as placeholder; used when the name is empty). */
    private val _suggestedName = MutableStateFlow<String?>(null)
    val suggestedName: StateFlow<String?> = _suggestedName.asStateFlow()
    private var suggestedFor: Uri? = null

    fun refreshSuggestedName() {
        val first = _items.value.firstOrNull() ?: run { _suggestedName.value = null; return }
        if (first.uri == suggestedFor) return
        suggestedFor = first.uri
        viewModelScope.launch {
            val text = withContext(Dispatchers.Default) { az.pdfcreator.app.pdf.Ocr.text(getApplication(), first.uri, first.rotation, 1600) }
            _suggestedName.value = az.pdfcreator.app.pdf.Ocr.suggestName(text)
        }
    }

    /** Text found in an image, for the "extract text" dialog. null = still working. */
    data class ExtractedText(val text: String?, val done: Boolean)

    private val _extracted = MutableStateFlow<ExtractedText?>(null)
    val extracted: StateFlow<ExtractedText?> = _extracted.asStateFlow()

    fun extractText(uri: Uri, rotation: Int = 0) {
        _extracted.value = ExtractedText(null, false)
        viewModelScope.launch {
            val text = withContext(Dispatchers.Default) { az.pdfcreator.app.pdf.Ocr.text(getApplication(), uri, rotation) }
            _extracted.value = ExtractedText(text, true)
        }
    }

    fun closeExtracted() { _extracted.value = null }

    // ---------------------------------------------------------- PDF editor

    data class EditorState(
        val sources: List<File> = emptyList(),
        val names: List<String> = emptyList(),
        val pages: List<az.pdfcreator.app.pdf.PdfEditor.PageRef> = emptyList(),
        val selected: Set<Long> = emptySet(),
        val busy: ImportState? = null,
        val askPassword: Uri? = null,
        val wrongPassword: Boolean = false,
        val exported: List<Uri>? = null,
    )

    private val _editor = MutableStateFlow(EditorState())
    val editor: StateFlow<EditorState> = _editor.asStateFlow()
    private val editDir = File(app.cacheDir, "edit")
    private val editIds = AtomicLong(1)

    fun editorOpen(uri: Uri, password: String? = null, append: Boolean = false) {
        if (!append && password == null) {
            editDir.listFiles()?.forEach { it.delete() }
            _editor.value = EditorState()
        }
        _editor.update { it.copy(busy = ImportState(0, 1), askPassword = null, wrongPassword = false) }
        viewModelScope.launch {
            val app = getApplication<Application>()
            val res = withContext(Dispatchers.IO) { runCatching { az.pdfcreator.app.pdf.PdfEditor.open(app, uri, editDir, password) } }
            res.onSuccess { (file, count) ->
                val name = queryName(uri)
                _editor.update { st ->
                    val src = st.sources.size
                    st.copy(
                        sources = st.sources + file,
                        names = st.names + name,
                        pages = st.pages + (0 until count).map { az.pdfcreator.app.pdf.PdfEditor.PageRef(editIds.getAndIncrement(), src, it) },
                        busy = null,
                    )
                }
            }.onFailure { e ->
                when (e) {
                    is az.pdfcreator.app.pdf.PdfEditor.PasswordNeeded -> _editor.update { it.copy(busy = null, askPassword = uri) }
                    is az.pdfcreator.app.pdf.PdfEditor.WrongPassword -> _editor.update { it.copy(busy = null, askPassword = uri, wrongPassword = true) }
                    else -> {
                        _editor.update { it.copy(busy = null) }
                        _events.value = UiEvent.PdfImportFailed(locked = false)
                    }
                }
            }
        }
    }

    fun editorCancelPassword() = _editor.update { it.copy(askPassword = null, wrongPassword = false) }

    fun editorMove(fromId: Long, toId: Long) = _editor.update { st ->
        val f = st.pages.indexOfFirst { it.id == fromId }
        val t = st.pages.indexOfFirst { it.id == toId }
        if (f < 0 || t < 0) st else st.copy(pages = st.pages.toMutableList().apply { add(t, removeAt(f)) })
    }

    fun editorRotate(id: Long) = _editor.update { st ->
        st.copy(pages = st.pages.map { if (it.id == id) it.copy(rotation = (it.rotation + 90) % 360) else it })
    }

    fun editorDelete(ids: Set<Long>) = _editor.update { st ->
        st.copy(pages = st.pages.filterNot { it.id in ids }, selected = st.selected - ids)
    }

    fun editorToggle(id: Long) = _editor.update { st ->
        st.copy(selected = if (id in st.selected) st.selected - id else st.selected + id)
    }

    fun editorClearSelection() = _editor.update { it.copy(selected = emptySet()) }

    fun editorSelectAll() = _editor.update { st -> st.copy(selected = st.pages.map { it.id }.toSet()) }

    /** Saves the pages (or only the selected ones) as a new PDF and opens the result screen. */
    fun editorSave(onlySelected: Boolean) {
        val st = _editor.value
        val pages = if (onlySelected) st.pages.filter { it.id in st.selected } else st.pages
        if (pages.isEmpty()) return
        val app = getApplication<Application>()
        val base = st.names.firstOrNull()?.removeSuffix(".pdf")?.removeSuffix(".PDF") ?: "PDF"
        val name = base + if (onlySelected) "_" + app.getString(az.pdfcreator.app.R.string.suffix_part) else "_" + app.getString(az.pdfcreator.app.R.string.suffix_edited)
        _gen.value = GenState.Running(0, 1)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val tmp = File(app.cacheDir, "out/edit_${System.nanoTime()}.pdf").apply { parentFile?.mkdirs() }
                    az.pdfcreator.app.pdf.PdfEditor.save(st.sources, pages, tmp)
                    val saved = az.pdfcreator.app.data.OutputSaver.save(app, tmp, name)
                    historyRepo.add(
                        HistoryEntry(saved.name, saved.uri.toString(), saved.filePath, saved.size, pages.size, System.currentTimeMillis(), false),
                    )
                    tmp.delete()
                    saved
                }
            }
            result.onSuccess { saved ->
                refreshHistory()
                _gen.value = GenState.Success(saved.uri, saved.name, pages.size, saved.size, 0, false, saved.location)
            }.onFailure { e ->
                _gen.value = GenState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Saves the pages (or the selection) as JPG images in Pictures/PDF Creator. */
    fun editorExportJpg(onlySelected: Boolean) {
        val st = _editor.value
        val pages = if (onlySelected) st.pages.filter { it.id in st.selected } else st.pages
        if (pages.isEmpty()) return
        val base = (st.names.firstOrNull() ?: "PDF").removeSuffix(".pdf").removeSuffix(".PDF")
        viewModelScope.launch {
            val uris = withContext(Dispatchers.IO) {
                az.pdfcreator.app.pdf.PdfEditor.exportJpg(getApplication(), st.sources, pages, base) { d, t ->
                    _editor.update { it.copy(busy = ImportState(d, t)) }
                }
            }
            _editor.update { it.copy(busy = null, exported = uris) }
        }
    }

    fun editorDismissExported() = _editor.update { it.copy(exported = null) }

    private fun queryName(uri: Uri): String = runCatching {
        getApplication<Application>().contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment ?: "PDF"

    // ----------------------------------------------------------- signature

    private val _hasSignature = MutableStateFlow(PdfWorker.signatureFile(app).exists())
    val hasSignature: StateFlow<Boolean> = _hasSignature.asStateFlow()
    private val _signatureVersion = MutableStateFlow(0)
    /** Changes whenever the signature image changes (for refreshing previews). */
    val signatureVersion: StateFlow<Int> = _signatureVersion.asStateFlow()

    fun saveSignature(bitmap: android.graphics.Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            val f = PdfWorker.signatureFile(getApplication())
            java.io.FileOutputStream(f).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            _hasSignature.value = true
            _signatureVersion.value++
        }
    }

    fun deleteSignature() {
        PdfWorker.signatureFile(getApplication()).delete()
        _hasSignature.value = false
        _signatureVersion.value++
        updateOptions { it.copy(signature = false) }
    }

    private fun observe(id: UUID) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(id).collect { info ->
                if (info == null) return@collect
                when (info.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> Unit
                    WorkInfo.State.RUNNING -> {
                        val total = info.progress.getInt(PdfWorker.PROGRESS_TOTAL, 0)
                        if (total > 0) _gen.value = GenState.Running(
                            info.progress.getInt(PdfWorker.PROGRESS_DONE, 0), total, info.progress.getInt(PdfWorker.PROGRESS_PASS, 1),
                        )
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        val d = info.outputData
                        _gen.value = GenState.Success(
                            uri = Uri.parse(d.getString(PdfWorker.OUT_URI)),
                            name = d.getString(PdfWorker.OUT_NAME) ?: "",
                            pages = d.getInt(PdfWorker.OUT_PAGES, 0),
                            size = d.getLong(PdfWorker.OUT_SIZE, 0),
                            skipped = d.getInt(PdfWorker.OUT_SKIPPED, 0),
                            protected = d.getBoolean(PdfWorker.OUT_PROTECTED, false),
                            location = d.getString(PdfWorker.OUT_LOCATION) ?: az.pdfcreator.app.data.OutputSaver.DEFAULT_LABEL,
                            overLimit = d.getBoolean(PdfWorker.OUT_OVER_LIMIT, false),
                            limitKb = lastLimitKb,
                        )
                        // The password is single-use; the file name too.
                        _options.update { it.copy(fileName = "", password = "") }
                        refreshHistory()
                        observeJob?.cancel()
                    }
                    WorkInfo.State.FAILED -> {
                        _gen.value = GenState.Failed(info.outputData.getString(PdfWorker.OUT_ERROR) ?: "")
                        observeJob?.cancel()
                    }
                    WorkInfo.State.CANCELLED -> {
                        _gen.value = GenState.Idle
                        observeJob?.cancel()
                    }
                }
            }
        }
    }

    fun cancelGeneration() {
        workManager.cancelUniqueWork(PdfWorker.UNIQUE_NAME)
        _gen.value = GenState.Idle
    }

    fun resetGeneration() { _gen.value = GenState.Idle }

    // ---------------------------------------------------------------- history

    fun refreshHistory() {
        viewModelScope.launch { _history.value = withContext(Dispatchers.IO) { historyRepo.load() } }
    }

    fun deleteHistory(entry: HistoryEntry, deleteFile: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { historyRepo.remove(entry, deleteFile) }
            refreshHistory()
        }
    }

    // --------------------------------------------------------------- settings

    fun finishOnboarding() {
        prefs.onboardingDone = true
        _onboardingDone.value = true
    }

    fun replayOnboarding() {
        prefs.onboardingDone = false
        _onboardingDone.value = false
    }

    fun setThemeMode(mode: Int) {
        prefs.themeMode = mode
        _themeMode.value = mode
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
