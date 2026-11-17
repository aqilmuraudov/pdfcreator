package az.pdfcreator.app.ui.screens

import android.text.format.Formatter
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.pdfcreator.app.R
import az.pdfcreator.app.model.PageItem
import az.pdfcreator.app.ui.MainViewModel
import az.pdfcreator.app.ui.theme.Brand
import coil.compose.AsyncImage
import coil.request.ImageRequest
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

/** The picked images: reorder, rotate, delete, then continue to PDF settings. */
@Composable
fun EditorScreen(vm: MainViewModel, onBack: () -> Unit, onCreate: () -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    val options by vm.options.collectAsStateWithLifecycle()
    val importState by vm.importState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var showSources by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Long?>(null) }
    val pick = rememberSourcePicker(vm, onPicked = {})

    val gridState = rememberLazyGridState()
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
        val fromKey = from.key as? Long ?: return@rememberReorderableLazyGridState
        val toKey = to.key as? Long ?: return@rememberReorderableLazyGridState
        vm.moveByKey(fromKey, toKey)
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            BackHeader(stringResource(R.string.new_pdf_title), onBack) {
                if (items.isNotEmpty()) {
                    Box {
                        SquareIconButton(Icons.Default.MoreVert, stringResource(R.string.more), { menuOpen = true })
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.reverse_order)) },
                                leadingIcon = { Icon(Icons.Default.SwapVert, null) },
                                onClick = { menuOpen = false; vm.reverse() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rotate_all)) },
                                leadingIcon = { Icon(Icons.Default.Rotate90DegreesCw, null) },
                                onClick = { menuOpen = false; vm.rotateAll() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.clear_all)) },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, null) },
                                onClick = { menuOpen = false; confirmClear = true },
                            )
                        }
                    }
                }
            }
            importState?.let { st ->
                LinearProgressIndicator(
                    progress = { if (st.total == 0) 0f else st.done.toFloat() / st.total },
                    color = Brand.Coral,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            if (items.isEmpty() && importState == null) {
                EmptyEditor { showSources = true }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    state = gridState,
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "summary", span = { GridItemSpan(maxLineSpan) }) {
                        SummaryCard(
                            count = items.size,
                            estimate = Formatter.formatShortFileSize(context, options.estimateBytes(items.size)),
                            pages = (items.size + options.imagesPerPage - 1) / options.imagesPerPage,
                            onCreate = onCreate,
                        )
                    }
                    item(key = "pagesHeader", span = { GridItemSpan(maxLineSpan) }) {
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.pages_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                                Text(stringResource(R.string.reorder_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Surface(
                                onClick = vm::reverse,
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                            ) {
                                Text(
                                    stringResource(R.string.reverse_short),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                )
                            }
                        }
                    }
                    itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                        ReorderableItem(reorderState, key = item.id) { isDragging ->
                            val elevation by animateDpAsState(if (isDragging) 12.dp else 0.dp, label = "drag")
                            LaunchedEffect(isDragging) {
                                if (isDragging) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                shadowElevation = elevation,
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.longPressDraggableHandle(),
                            ) {
                                PageTile(index, item, onOpen = { preview = item.id }, onRotate = { vm.rotate(item.id) }, onRemove = { vm.remove(item.id) })
                            }
                        }
                    }
                }
            }
        }

        // Add button
        Surface(
            onClick = { showSources = true },
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 10.dp,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 22.dp, bottom = 24.dp).size(68.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, stringResource(R.string.add_images), modifier = Modifier.size(30.dp)) }
        }
    }

    if (showSources) SourceSheet(onDismiss = { showSources = false }, onPick = pick)

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_all_q)) },
            text = { Text(stringResource(R.string.clear_all_desc)) },
            confirmButton = { TextButton(onClick = { confirmClear = false; vm.clear() }) { Text(stringResource(R.string.clear)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    preview?.let { id ->
        val current = items.firstOrNull { it.id == id }
        if (current == null) {
            LaunchedEffect(id) { preview = null }
        } else {
            PreviewDialog(
                item = current,
                index = items.indexOf(current),
                onDismiss = { preview = null },
                onRotate = { vm.rotate(current.id) },
                onStraighten = { vm.straighten(current.id) },
                onText = { vm.extractText(current.uri, current.rotation) },
                onDelete = { vm.remove(current.id); preview = null },
            )
        }
    }
}

@Composable
private fun SummaryCard(count: Int, estimate: String, pages: Int, onCreate: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brand.Coral)) {
        // Decorative shapes
        Box(Modifier.align(Alignment.TopEnd).offset(x = 30.dp, y = (-30).dp).size(140.dp).clip(CircleShape).background(Brand.Yellow))
        Box(Modifier.align(Alignment.TopEnd).offset(x = (-34).dp, y = 46.dp).size(width = 54.dp, height = 70.dp).rotate(8f).clip(RoundedCornerShape(8.dp)).background(Color.White))
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$count", fontSize = 60.sp, lineHeight = 60.sp, fontWeight = FontWeight.Black, color = Brand.Navy)
                Spacer(Modifier.width(10.dp))
                Text(pluralStringResource(R.plurals.images_word, count), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Brand.Navy, modifier = Modifier.padding(bottom = 8.dp))
            }
            Text(stringResource(R.string.pdf_pages_est, pages, estimate), color = Brand.Navy, fontWeight = FontWeight.SemiBold)
            Surface(onClick = onCreate, shape = RoundedCornerShape(26.dp), color = Brand.Navy, contentColor = Color.White) {
                Row(Modifier.padding(horizontal = 24.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.create_pdf), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun PageTile(index: Int, item: PageItem, onOpen: () -> Unit, onRotate: () -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    Box(Modifier.fillMaxWidth().aspectRatio(0.82f).clickable(onClick = onOpen)) {
        Box(
            Modifier.fillMaxSize().padding(6.dp).clip(RoundedCornerShape(17.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(item.uri).size(400).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(6.dp).rotate(item.rotation.toFloat()),
            )
        }
        Box(
            Modifier.align(Alignment.TopStart).padding(12.dp).size(30.dp).clip(CircleShape).background(Brand.Yellow),
            contentAlignment = Alignment.Center,
        ) {
            Text("${index + 1}", color = Brand.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
        RoundAction(Icons.Default.Close, stringResource(R.string.delete), onRemove, Modifier.align(Alignment.TopEnd).padding(8.dp))
        RoundAction(Icons.Default.Rotate90DegreesCw, stringResource(R.string.rotate), onRotate, Modifier.align(Alignment.BottomEnd).padding(8.dp))
    }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    // 44dp touch target with a 34dp visual circle.
    Box(modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.94f)), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Brand.Navy, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun EmptyEditor(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(30.dp)).background(Brand.Coral), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Add, null, tint = Brand.Navy, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.empty_subtitle), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        BigActionButton(stringResource(R.string.add_images), onAdd)
    }
}

@Composable
private fun PreviewDialog(item: PageItem, index: Int, onDismiss: () -> Unit, onRotate: () -> Unit, onStraighten: () -> Unit, onText: () -> Unit, onDelete: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Brand.Navy) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.close), tint = Color.White) }
                    Text(stringResource(R.string.page_n, index + 1), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onText) { Icon(Icons.Default.TextSnippet, stringResource(R.string.extract_text), tint = Color.White) }
                    IconButton(onClick = onStraighten) { Icon(Icons.Default.AutoFixHigh, stringResource(R.string.straighten), tint = Brand.Yellow) }
                    IconButton(onClick = onRotate) { Icon(Icons.Default.Rotate90DegreesCw, stringResource(R.string.rotate), tint = Color.White) }
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = Color.White) }
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().rotate(item.rotation.toFloat()),
                    )
                }
            }
        }
    }
}
