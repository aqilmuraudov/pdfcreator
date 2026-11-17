package az.pdfcreator.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import az.pdfcreator.app.R
import az.pdfcreator.app.ui.MainViewModel
import az.pdfcreator.app.ui.theme.Brand
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

enum class Source(val icon: ImageVector, val label: Int, val desc: Int) {
    GALLERY(Icons.Default.PhotoLibrary, R.string.src_gallery, R.string.src_gallery_desc),
    FILES(Icons.Default.FolderOpen, R.string.src_files, R.string.src_files_desc),
    SCAN(Icons.Default.DocumentScanner, R.string.src_scan, R.string.src_scan_desc),
    PDF(Icons.Default.PictureAsPdf, R.string.src_merge, R.string.src_pdf_desc),
}

/**
 * All image sources in one place. [onPicked] runs when something was actually chosen
 * (used to jump from the home screen to the editor).
 */
@Composable
fun rememberSourcePicker(vm: MainViewModel, onPicked: () -> Unit): (Source) -> Unit {
    val context = LocalContext.current
    val picked by rememberUpdatedState(onPicked)

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) {
        if (it.isNotEmpty()) { vm.addUris(it); picked() }
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        if (it.isNotEmpty()) { vm.addUris(it); picked() }
    }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { vm.importPdf(uri); picked() }
    }
    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uris = GmsDocumentScanningResult.fromActivityResultIntent(res.data)?.pages?.map { it.imageUri }.orEmpty()
            if (uris.isNotEmpty()) { vm.addScanned(uris); picked() }
        }
    }

    return { source ->
        when (source) {
            Source.GALLERY -> gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            Source.FILES -> files.launch(arrayOf("image/*"))
            Source.PDF -> pdf.launch(arrayOf("application/pdf"))
            Source.SCAN -> {
                val activity = context.findActivity()
                if (activity != null) {
                    val options = GmsDocumentScannerOptions.Builder()
                        .setGalleryImportAllowed(true)
                        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                        .build()
                    GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                        .addOnSuccessListener { scanner.launch(IntentSenderRequest.Builder(it).build()) }
                        .addOnFailureListener {
                            Toast.makeText(context, R.string.scanner_unavailable, Toast.LENGTH_LONG).show()
                        }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSheet(onDismiss: () -> Unit, onPick: (Source) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.source_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 6.dp, start = 4.dp),
            )
            Source.entries.forEach { s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onDismiss(); onPick(s) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(15.dp))
                            .background(if (s == Source.GALLERY) Brand.Coral else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(s.icon, null, tint = if (s == Source.GALLERY) Brand.Navy else MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(s.label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(s.desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** White rounded-square icon button used in top bars. */
@Composable
fun SquareIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, size: Dp = 46.dp) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription, modifier = Modifier.size(22.dp)) }
    }
}

@Composable
fun BackHeader(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SquareIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), onBack)
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** White rounded card section. */
@Composable
fun BrandCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun CardLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Navy primary action with a coral arrow square, as in the designs. */
@Composable
fun BigActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth().height(64.dp),
    ) {
        Row(Modifier.padding(start = 24.dp, end = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(Brand.Coral),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Brand.Navy)
            }
        }
    }
}

/** Stable tile colour for a string (recent PDFs, placeholders). */
fun tileColor(key: String): Color = Brand.Tiles[(key.hashCode() and Int.MAX_VALUE) % Brand.Tiles.size]

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}


/** Shows OCR results with copy / share. Driven by [MainViewModel.extracted]. */
@Composable
fun ExtractedTextDialog(vm: MainViewModel) {
    val state by vm.extracted.collectAsStateWithLifecycle()
    val st = state ?: return
    val context = LocalContext.current
    androidx.compose.ui.window.Dialog(onDismissRequest = vm::closeExtracted) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.extract_text), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                if (!st.done) {
                    androidx.compose.material3.LinearProgressIndicator(color = Brand.Coral, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.extracting), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (st.text.isNullOrBlank()) {
                    Text(stringResource(R.string.no_text_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    androidx.compose.foundation.text.selection.SelectionContainer(
                        Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.background)
                            .padding(14.dp),
                    ) {
                        Text(st.text, modifier = Modifier.verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(stringResource(R.string.ocr_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            onClick = {
                                val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("text", st.text))
                                Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(18.dp), color = Brand.Navy, contentColor = Color.White,
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) { Box(contentAlignment = Alignment.Center) { Text(stringResource(R.string.copy), fontWeight = FontWeight.ExtraBold) } }
                        Surface(
                            onClick = {
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(android.content.Intent.EXTRA_TEXT, st.text)
                                context.startActivity(android.content.Intent.createChooser(send, context.getString(R.string.share)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                            },
                            shape = RoundedCornerShape(18.dp), color = Brand.Yellow, contentColor = Brand.Navy,
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) { Box(contentAlignment = Alignment.Center) { Text(stringResource(R.string.share), fontWeight = FontWeight.ExtraBold) } }
                    }
                }
                androidx.compose.material3.TextButton(onClick = vm::closeExtracted, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}
