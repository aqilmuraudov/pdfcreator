package az.pdfcreator.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.pdfcreator.app.R
import az.pdfcreator.app.pdf.PdfEditor
import az.pdfcreator.app.ui.MainViewModel
import az.pdfcreator.app.ui.theme.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

/** Lossless PDF editing: reorder / rotate / delete pages, merge PDFs, save a selection, export JPG. */
@Composable
fun PdfToolsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val st by vm.editor.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var appendNext by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.editorOpen(uri, append = appendNext)
    }
    val open: (Boolean) -> Unit = { append -> appendNext = append; picker.launch(arrayOf("application/pdf")) }

    // Thumbnails: rendered off the main thread, one at a time, cached.
    val cache = remember { LruCache<String, ImageBitmap>(120) }
    val renderLock = remember { Mutex() }

    val gridState = rememberLazyGridState()
    val reorder = rememberReorderableLazyGridState(gridState) { from, to ->
        val f = from.key as? Long ?: return@rememberReorderableLazyGridState
        val t = to.key as? Long ?: return@rememberReorderableLazyGridState
        vm.editorMove(f, t)
    }
    val selecting = st.selected.isNotEmpty()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            BackHeader(stringResource(R.string.pdf_edit_title), onBack) {
                if (st.pages.isNotEmpty()) {
                    SquareIconButton(
                        if (selecting) Icons.Default.Close else Icons.Default.DoneAll,
                        stringResource(R.string.select_all),
                        { if (selecting) vm.editorClearSelection() else vm.editorSelectAll() },
                    )
                }
            }
            st.busy?.let { b ->
                LinearProgressIndicator(
                    progress = { if (b.total == 0) 0f else b.done.toFloat() / b.total },
                    color = Brand.Coral,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            if (st.pages.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.size(100.dp).clip(RoundedCornerShape(30.dp)).background(Brand.Coral), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PictureAsPdf, null, tint = Brand.Navy, modifier = Modifier.size(48.dp))
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(stringResource(R.string.pdf_edit_empty), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp))
                    if (st.busy == null) BigActionButton(stringResource(R.string.open_pdf), { open(false) })
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (selecting) stringResource(R.string.selected_count, st.selected.size) else stringResource(R.string.pages_count, st.pages.size),
                            fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium,
                        )
                        Text(stringResource(R.string.edit_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    state = gridState,
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 130.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(st.pages, key = { _, p -> p.id }) { index, page ->
                        ReorderableItem(reorder, key = page.id) { dragging ->
                            val elevation by animateDpAsState(if (dragging) 12.dp else 0.dp, label = "drag")
                            LaunchedEffect(dragging) { if (dragging) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                            val selected = page.id in st.selected
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                shadowElevation = elevation,
                                color = MaterialTheme.colorScheme.surface,
                                border = if (selected) BorderStroke(3.dp, Brand.Coral) else null,
                                modifier = Modifier.longPressDraggableHandle(),
                            ) {
                                Box(Modifier.fillMaxWidth().aspectRatio(0.72f).clickable { vm.editorToggle(page.id) }) {
                                    val file = st.sources.getOrNull(page.src)
                                    val key = "${file?.name}#${page.index}"
                                    val thumb by produceState(cache.get(key), key) {
                                        if (value == null && file != null) {
                                            value = renderLock.withLock {
                                                withContext(Dispatchers.IO) { PdfEditor.render(file, page.index, 0, 360)?.asImageBitmap() }
                                            }?.also { cache.put(key, it) }
                                        }
                                    }
                                    Box(Modifier.fillMaxSize().padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                                        thumb?.let {
                                            Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().rotate(page.rotation.toFloat()))
                                        }
                                    }
                                    Box(
                                        Modifier.align(Alignment.TopStart).padding(12.dp).size(26.dp).clip(CircleShape)
                                            .background(if (selected) Brand.Coral else Brand.Yellow),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (selected) Icon(Icons.Default.Check, null, tint = Brand.Navy, modifier = Modifier.size(16.dp))
                                        else Text("${index + 1}", color = Brand.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                    }
                                    MiniAction(Icons.Default.Rotate90DegreesCw, stringResource(R.string.rotate), Modifier.align(Alignment.BottomEnd)) { vm.editorRotate(page.id) }
                                    MiniAction(Icons.Default.Delete, stringResource(R.string.delete), Modifier.align(Alignment.BottomStart)) { vm.editorDelete(setOf(page.id)) }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (st.pages.isNotEmpty()) {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (selecting) {
                    BarButton(Icons.Default.Delete, null, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.error) { vm.editorDelete(st.selected) }
                    BarButton(Icons.Default.Image, "JPG", Brand.Yellow, Brand.Navy) { vm.editorExportJpg(true) }
                    BarButton(Icons.Default.Save, stringResource(R.string.save_selected), Brand.Navy, Color.White, Modifier.weight(1f)) { vm.editorSave(true) }
                } else {
                    BarButton(Icons.Default.Add, "PDF", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface) { open(true) }
                    BarButton(Icons.Default.Image, "JPG", Brand.Yellow, Brand.Navy) { vm.editorExportJpg(false) }
                    BarButton(Icons.Default.Save, stringResource(R.string.save_pdf), Brand.Navy, Color.White, Modifier.weight(1f)) { vm.editorSave(false) }
                }
            }
        }
    }

    // Password for protected PDFs.
    st.askPassword?.let { uri ->
        var pw by remember(uri) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = vm::editorCancelPassword,
            title = { Text(stringResource(R.string.pdf_password_title), fontWeight = FontWeight.ExtraBold) },
            text = {
                OutlinedTextField(
                    value = pw,
                    onValueChange = { pw = it },
                    label = { Text(stringResource(R.string.password)) },
                    isError = st.wrongPassword,
                    supportingText = { if (st.wrongPassword) Text(stringResource(R.string.pdf_password_wrong)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
            },
            confirmButton = { TextButton(onClick = { vm.editorOpen(uri, pw, append = st.pages.isNotEmpty()) }) { Text(stringResource(R.string.unlock)) } },
            dismissButton = { TextButton(onClick = vm::editorCancelPassword) { Text(stringResource(R.string.cancel)) } },
        )
    }

    // JPG export finished.
    st.exported?.let { uris ->
        AlertDialog(
            onDismissRequest = vm::editorDismissExported,
            title = { Text(stringResource(R.string.save_as_jpg), fontWeight = FontWeight.ExtraBold) },
            text = { Text(stringResource(R.string.jpg_saved, uris.size)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.editorDismissExported()
                    if (uris.isNotEmpty()) shareImages(context, uris)
                }) { Text(stringResource(R.string.share)) }
            },
            dismissButton = { TextButton(onClick = vm::editorDismissExported) { Text(stringResource(R.string.close)) } },
        )
    }

    GenerationDialogs(vm)
}

private fun shareImages(context: android.content.Context, uris: List<Uri>) {
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
        .setType("image/jpeg")
        .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
private fun MiniAction(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.95f)), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Brand.Navy, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String?, bg: Color, fg: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = bg, contentColor = fg, shadowElevation = 6.dp, modifier = modifier.height(58.dp)) {
        Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, label, modifier = Modifier.size(20.dp))
            if (label != null) {
                Spacer(Modifier.width(8.dp))
                Text(label, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }
        }
    }
}
