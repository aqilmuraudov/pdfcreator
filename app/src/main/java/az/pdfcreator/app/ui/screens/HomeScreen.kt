package az.pdfcreator.app.ui.screens

import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.pdfcreator.app.R
import az.pdfcreator.app.model.HistoryEntry
import az.pdfcreator.app.ui.MainViewModel
import az.pdfcreator.app.ui.theme.Brand

/** Home: pick a source, see recent PDFs, continue unfinished work. */
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onOpenEditor: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onIdCard: () -> Unit = {},
    onPdfTools: () -> Unit = {},
) {
    val textPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) vm.extractText(uri) }
    var signatureDialog by remember { mutableStateOf(false) }
    val items by vm.items.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val pick = rememberSourcePicker(vm, onPicked = onOpenEditor)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        // Header
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 20.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_welcome), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            }
            SquareIconButton(Icons.Default.History, stringResource(R.string.history), onHistory)
            Spacer(Modifier.width(8.dp))
            SquareIconButton(Icons.Default.Tune, stringResource(R.string.settings), onSettings)
        }

        // Unfinished work
        if (items.isNotEmpty()) {
            Surface(
                onClick = onOpenEditor,
                shape = RoundedCornerShape(24.dp),
                color = Brand.Yellow,
                contentColor = Brand.Navy,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.continue_title), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                        Text(pluralStringResource(R.plurals.continue_text, items.size, items.size), style = MaterialTheme.typography.bodyMedium)
                    }
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(Brand.Navy), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.continue_btn), tint = Color.White)
                    }
                }
            }
        }

        // New PDF card
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp)
                .clip(RoundedCornerShape(30.dp)).background(Brand.Coral).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.new_pdf_title), color = Brand.Navy, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.choose_source), color = Brand.Navy, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.4f)).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SourceTile(Source.GALLERY, true, Modifier.weight(1f)) { pick(it) }
                SourceTile(Source.FILES, false, Modifier.weight(1f)) { pick(it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SourceTile(Source.SCAN, false, Modifier.weight(1f)) { pick(it) }
                SourceTile(Source.PDF, false, Modifier.weight(1f)) { pick(it) }
            }
        }

        // Document tools
        Text(
            stringResource(R.string.tools_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 22.dp, top = 22.dp, bottom = 10.dp),
        )
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolTile(Icons.Default.Badge, Brand.Yellow, stringResource(R.string.tool_id_card), stringResource(R.string.tool_id_card_desc), Modifier.weight(1f), onIdCard)
            ToolTile(Icons.Default.Draw, Brand.Mist, stringResource(R.string.my_signature), stringResource(R.string.tool_signature_desc), Modifier.weight(1f)) { signatureDialog = true }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolTile(Icons.Default.EditNote, Brand.Coral, stringResource(R.string.tool_pdf_edit), stringResource(R.string.tool_pdf_edit_desc), Modifier.weight(1f), onPdfTools)
            ToolTile(Icons.Default.TextSnippet, Brand.Mist, stringResource(R.string.extract_text), stringResource(R.string.tool_extract_desc), Modifier.weight(1f)) {
                textPicker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }

        // Recent PDFs
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.recent_pdfs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            if (history.isNotEmpty()) {
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onHistory).padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.see_all), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp))
                }
            }
        }
        if (history.isEmpty()) {
            Text(
                stringResource(R.string.recent_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
            )
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(history.take(10), key = { it.uri }) { RecentCard(it) }
            }
        }

        // Tip
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp)
                .clip(RoundedCornerShape(24.dp)).background(Brand.Navy).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(Brand.Yellow), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Lightbulb, null, tint = Brand.Navy, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.tip_share), color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (signatureDialog) SignatureManagerDialog(vm) { signatureDialog = false }
}

@Composable
private fun ToolTile(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, title: String, desc: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(tint), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Brand.Navy, modifier = Modifier.size(22.dp))
            }
            Text(title, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleSmall)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2)
        }
    }
}

@Composable
private fun SourceTile(source: Source, primary: Boolean, modifier: Modifier, onClick: (Source) -> Unit) {
    Surface(
        onClick = { onClick(source) },
        shape = RoundedCornerShape(22.dp),
        color = if (primary) Brand.Navy else Color.White,
        contentColor = if (primary) Color.White else Brand.Navy,
        modifier = modifier.height(96.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (primary) Color.White.copy(alpha = 0.14f) else Brand.Lavender),
                contentAlignment = Alignment.Center,
            ) { Icon(source.icon, null, modifier = Modifier.size(21.dp)) }
            Text(stringResource(source.label), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun RecentCard(entry: HistoryEntry) {
    val context = LocalContext.current
    Surface(
        onClick = { openPdf(context, Uri.parse(entry.uri)) },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.width(150.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(16.dp)).background(tileColor(entry.name))) {
                Text(
                    stringResource(R.string.pages_short, entry.pages),
                    color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(8.dp)).background(Brand.Navy).padding(horizontal = 8.dp, vertical = 3.dp),
                )
                if (entry.protected) {
                    Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(18.dp))
                }
            }
            Column(Modifier.padding(horizontal = 2.dp)) {
                Text(entry.name.removeSuffix(".pdf"), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    Formatter.formatShortFileSize(context, entry.sizeBytes) + " · " +
                        DateUtils.getRelativeTimeSpanString(entry.createdAt, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
