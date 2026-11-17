package az.pdfcreator.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.pdfcreator.app.BuildConfig
import az.pdfcreator.app.R
import az.pdfcreator.app.data.OutputSaver
import az.pdfcreator.app.model.HistoryEntry
import az.pdfcreator.app.ui.GenState
import az.pdfcreator.app.ui.MainViewModel
import az.pdfcreator.app.ui.theme.Brand

// ------------------------------------------------------------------ helpers

fun openPdf(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/pdf")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_pdf_viewer, Toast.LENGTH_LONG).show()
    }
}

fun sharePdf(context: Context, uri: Uri, name: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, name)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

// ------------------------------------------------------------------- result

@Composable
fun ResultScreen(
    result: GenState.Success,
    formatLabel: String,
    onNewPdf: () -> Unit,
    onClose: () -> Unit,
    onBackToPages: () -> Unit,
) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().background(Brand.Coral)) {
        Box(Modifier.offset(x = (-60).dp, y = 140.dp).size(220.dp).clip(CircleShape).background(Brand.Yellow))
        Box(Modifier.align(Alignment.TopEnd).offset(x = 40.dp, y = 60.dp).size(120.dp).rotate(18f).clip(RoundedCornerShape(30.dp)).background(Brand.Navy))

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, start = 20.dp, end = 20.dp), horizontalArrangement = Arrangement.Start) {
                Surface(onClick = onClose, shape = RoundedCornerShape(15.dp), color = Color.White, contentColor = Brand.Navy, modifier = Modifier.size(46.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Bottom),
            ) {
                Text(stringResource(R.string.done_title), fontSize = 46.sp, lineHeight = 50.sp, fontWeight = FontWeight.Black, color = Brand.Navy, modifier = Modifier.padding(start = 4.dp))

                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color.White).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(width = 56.dp, height = 68.dp).clip(RoundedCornerShape(12.dp)).background(Brand.Navy),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            if (result.protected) {
                                Icon(Icons.Default.Lock, stringResource(R.string.password_protect), tint = Brand.Coral, modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp).size(18.dp))
                            }
                            Text("PDF", color = Brand.Coral, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(result.name, color = Brand.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(result.location, color = Brand.Muted, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat("${result.pages}", stringResource(R.string.stat_pages), Modifier.weight(1f))
                        Stat(Formatter.formatShortFileSize(context, result.size), stringResource(R.string.stat_size), Modifier.weight(1f))
                        Stat(formatLabel, stringResource(R.string.stat_format), Modifier.weight(1f))
                    }
                    if (result.overLimit && result.limitKb > 0) {
                        Text(
                            stringResource(
                                R.string.over_limit,
                                "${result.limitKb / 1024} MB",
                                Formatter.formatShortFileSize(context, result.size),
                            ),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (result.skipped > 0) {
                        Text(stringResource(R.string.skipped_images, result.skipped), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(onClick = { openPdf(context, result.uri) }, shape = RoundedCornerShape(20.dp), color = Brand.Navy, contentColor = Color.White, modifier = Modifier.weight(1f).height(56.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text(stringResource(R.string.open), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
                        }
                        Surface(onClick = { sharePdf(context, result.uri, result.name) }, shape = RoundedCornerShape(20.dp), color = Brand.Yellow, contentColor = Brand.Navy, modifier = Modifier.weight(1f).height(56.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text(stringResource(R.string.share), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
                        }
                    }
                }

                Surface(
                    onClick = onNewPdf,
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Transparent,
                    contentColor = Brand.Navy,
                    border = BorderStroke(2.5.dp, Brand.Navy),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("+ " + stringResource(R.string.new_pdf), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
                }
                Text(
                    stringResource(R.string.back_to_pages),
                    color = Brand.Navy,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onBackToPages).padding(12.dp),
                )
            }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Brand.Lavender).padding(12.dp)) {
        Text(value, color = Brand.Navy, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = Brand.Muted, style = MaterialTheme.typography.labelMedium)
    }
}

// ------------------------------------------------------------------ history

@Composable
fun HistoryScreen(vm: MainViewModel, onBack: () -> Unit, onEdit: (Uri) -> Unit = {}) {
    val history by vm.history.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var toDelete by remember { mutableStateOf<HistoryEntry?>(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        BackHeader(stringResource(R.string.history), onBack)
        if (history.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(96.dp).clip(RoundedCornerShape(30.dp)).background(Brand.Yellow), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.History, null, tint = Brand.Navy, modifier = Modifier.size(44.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.history_empty), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.navigationBarsPadding(),
            ) {
                items(history, key = { it.uri }) { entry ->
                    var menu by remember { mutableStateOf(false) }
                    Surface(
                        onClick = { openPdf(context, Uri.parse(entry.uri)) },
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(width = 48.dp, height = 58.dp).clip(RoundedCornerShape(12.dp)).background(tileColor(entry.name)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (entry.protected) Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                else Text("PDF", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.name, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    stringResource(R.string.pages_and_size, entry.pages, Formatter.formatShortFileSize(context, entry.sizeBytes)) +
                                        " · " + DateUtils.getRelativeTimeSpanString(entry.createdAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Box {
                                Surface(onClick = { menu = true }, shape = CircleShape, color = Color.Transparent, modifier = Modifier.size(44.dp)) {
                                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.MoreVert, stringResource(R.string.more)) }
                                }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.open)) },
                                        leadingIcon = { Icon(Icons.Default.Visibility, null) },
                                        onClick = { menu = false; openPdf(context, Uri.parse(entry.uri)) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.share)) },
                                        leadingIcon = { Icon(Icons.Default.Share, null) },
                                        onClick = { menu = false; sharePdf(context, Uri.parse(entry.uri), entry.name) },
                                    )
                                    if (!entry.protected) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.edit_pdf)) },
                                            leadingIcon = { Icon(Icons.Default.EditNote, null) },
                                            onClick = { menu = false; onEdit(Uri.parse(entry.uri)) },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.delete)) },
                                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                                        onClick = { menu = false; toDelete = entry },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.delete_pdf_q), fontWeight = FontWeight.ExtraBold) },
            text = { Text(entry.name) },
            confirmButton = {
                TextButton(onClick = { vm.deleteHistory(entry, deleteFile = true); toDelete = null }) {
                    Text(stringResource(R.string.delete_file), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.deleteHistory(entry, deleteFile = false); toDelete = null }) { Text(stringResource(R.string.remove_from_list)) }
                    TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }
}

// ----------------------------------------------------------------- settings

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit, onAbout: () -> Unit) {
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val autoStraighten by vm.autoStraighten.collectAsStateWithLifecycle()
    val snapA4 by vm.snapA4.collectAsStateWithLifecycle()
    val saveFolder by vm.saveFolder.collectAsStateWithLifecycle()
    var langDialog by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf(false) }
    var folderDialog by remember { mutableStateOf(false) }
    var signatureDialog by remember { mutableStateOf(false) }
    val hasSignature by vm.hasSignature.collectAsStateWithLifecycle()
    val currentLang = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-').substringBefore(',')
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setSaveFolder(uri)
    }

    val languages = listOf(
        "" to stringResource(R.string.lang_system),
        "az" to "Azərbaycanca",
        "en" to "English",
        "ru" to "Русский",
    )
    val themes = listOf(
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM to stringResource(R.string.theme_system),
        AppCompatDelegate.MODE_NIGHT_NO to stringResource(R.string.theme_light),
        AppCompatDelegate.MODE_NIGHT_YES to stringResource(R.string.theme_dark),
    )

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        BackHeader(stringResource(R.string.settings), onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 30.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingRow(Icons.Default.Language, Brand.Coral, stringResource(R.string.language), languages.firstOrNull { it.first == currentLang }?.second ?: languages[0].second) { langDialog = true }
            SettingRow(Icons.Default.Palette, Brand.Yellow, stringResource(R.string.theme), themes.firstOrNull { it.first == themeMode }?.second ?: themes[0].second) { themeDialog = true }
            ToggleRow(Icons.Default.AutoFixHigh, Brand.Coral, stringResource(R.string.auto_straighten), stringResource(R.string.auto_straighten_desc), autoStraighten) { vm.setAutoStraighten(it) }
            ToggleRow(Icons.Default.AspectRatio, Brand.Yellow, stringResource(R.string.snap_a4), stringResource(R.string.snap_a4_desc), snapA4) { vm.setSnapA4(it) }
            SettingRow(Icons.Default.Draw, Brand.Mist, stringResource(R.string.my_signature), stringResource(if (hasSignature) R.string.signature_saved_desc else R.string.signature_none)) { signatureDialog = true }
            SettingRow(Icons.Default.Folder, Brand.Yellow, stringResource(R.string.save_location), saveFolder ?: OutputSaver.DEFAULT_LABEL) { folderDialog = true }
            SettingRow(Icons.Default.Info, Brand.Mist, stringResource(R.string.about), stringResource(R.string.about_version_line, BuildConfig.VERSION_NAME), onAbout)
        }
    }

    if (signatureDialog) SignatureManagerDialog(vm) { signatureDialog = false }
    if (folderDialog) {
        AlertDialog(
            onDismissRequest = { folderDialog = false },
            title = { Text(stringResource(R.string.save_location), fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FolderOption(
                        title = stringResource(R.string.folder_default),
                        subtitle = OutputSaver.DEFAULT_LABEL,
                        selected = saveFolder == null,
                    ) { vm.resetSaveFolder(); folderDialog = false }
                    if (saveFolder != null) {
                        FolderOption(title = stringResource(R.string.folder_custom), subtitle = saveFolder!!, selected = true) { folderDialog = false }
                    }
                    Surface(
                        onClick = { folderDialog = false; folderPicker.launch(null) },
                        shape = RoundedCornerShape(16.dp),
                        color = Brand.Navy,
                        contentColor = Color.White,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CreateNewFolder, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.choose_folder), fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    Text(stringResource(R.string.folder_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { folderDialog = false }) { Text(stringResource(R.string.close)) } },
        )
    }
    if (langDialog) {
        ChoiceDialog(
            title = stringResource(R.string.language),
            options = languages,
            selected = languages.firstOrNull { it.first == currentLang }?.first ?: "",
            onSelect = { tag ->
                langDialog = false
                AppCompatDelegate.setApplicationLocales(
                    if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
                )
            },
            onDismiss = { langDialog = false },
        )
    }
    if (themeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.theme),
            options = themes,
            selected = themeMode,
            onSelect = { themeDialog = false; vm.setThemeMode(it) },
            onDismiss = { themeDialog = false },
        )
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, tint: Color, title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface)
            .clickable { onChange(!checked) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(tint), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Brand.Navy, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.ExtraBold)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Brand.Navy, checkedThumbColor = Brand.Yellow),
        )
    }
}

@Composable
private fun FolderOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (selected) Brand.Coral.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = Brand.Coral))
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// -------------------------------------------------------------------- about

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        BackHeader(stringResource(R.string.about), onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 30.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Hero
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Brand.Coral)) {
                Box(Modifier.align(Alignment.TopEnd).offset(x = 40.dp, y = (-40).dp).size(170.dp).clip(CircleShape).background(Brand.Yellow))
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.size(width = 64.dp, height = 78.dp).rotate(-6f).clip(RoundedCornerShape(14.dp)).background(Brand.Navy),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Text("PDF", color = Brand.Coral, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.app_name), color = Brand.Navy, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Text(stringResource(R.string.about_tagline), color = Brand.Navy, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.about_version_line, BuildConfig.VERSION_NAME),
                        color = Brand.Navy, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.45f)).padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            // Made by
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brand.Navy).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(Brand.Yellow), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = Brand.Navy, modifier = Modifier.size(28.dp))
                }
                Box(
                    Modifier.offset(x = (-12).dp).size(52.dp).clip(CircleShape).background(Brand.Coral),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Brand.Navy, modifier = Modifier.size(26.dp))
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(stringResource(R.string.made_with_title), color = Color(0xFFC9C8DE), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.credits), color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                }
            }

            // Features
            Text(stringResource(R.string.about_features), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
            val features = listOf(
                Triple(Icons.Default.Bolt, R.string.onb3_fast, R.string.feat_fast_desc),
                Triple(Icons.Default.Lock, R.string.onb3_private, R.string.feat_private_desc),
                Triple(Icons.Default.WifiOff, R.string.onb3_offline, R.string.feat_offline_desc),
                Triple(Icons.Default.AllInclusive, R.string.feat_unlimited, R.string.feat_unlimited_desc),
                Triple(Icons.Default.AutoFixHigh, R.string.feat_smart_scan, R.string.feat_smart_scan_desc),
                Triple(Icons.Default.Favorite, R.string.feat_free, R.string.feat_free_desc),
            )
            val tints = listOf(Brand.Coral, Brand.Yellow, Brand.Mist, Brand.Yellow, Brand.Coral, Brand.Mist)
            features.chunked(2).forEachIndexed { row, pair ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEachIndexed { col, (icon, title, desc) ->
                        Column(
                            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(tints[row * 2 + col]), contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = Brand.Navy, modifier = Modifier.size(22.dp))
                            }
                            Text(stringResource(title), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Info
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(vertical = 6.dp)) {
                InfoLine(Icons.Default.Info, stringResource(R.string.about_version), BuildConfig.VERSION_NAME)
                InfoLine(Icons.Default.Language, stringResource(R.string.about_languages), "AZ · EN · RU")
                InfoLine(Icons.Default.PhoneAndroid, stringResource(R.string.about_platform), "Android 8.0+")
                InfoLine(Icons.Default.Spellcheck, stringResource(R.string.about_dictionary), "mozillaz · MPL-2.0")
            }
        }
    }
}

@Composable
private fun InfoLine(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        // Label keeps its natural width; the value takes the rest and wraps if needed.
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
        Spacer(Modifier.width(12.dp))
        Text(value, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SettingRow(icon: ImageVector, tint: Color, title: String, value: String?, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(tint), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Brand.Navy, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.ExtraBold)
            if (value != null) Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClick != null) Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSelect(value) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = { onSelect(value) },
                            colors = RadioButtonDefaults.colors(selectedColor = Brand.Coral),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, fontWeight = if (value == selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
