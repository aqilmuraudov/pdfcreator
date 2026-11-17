package az.pdfcreator.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.text.format.Formatter
import az.pdfcreator.app.R
import az.pdfcreator.app.model.ColorMode
import az.pdfcreator.app.model.MarginOption
import az.pdfcreator.app.model.OrientationOption
import az.pdfcreator.app.model.PageSizeOption
import az.pdfcreator.app.model.QualityOption
import az.pdfcreator.app.model.ScaleMode
import az.pdfcreator.app.model.SignatureInk
import az.pdfcreator.app.model.SignaturePages
import az.pdfcreator.app.model.SignaturePos
import az.pdfcreator.app.ui.GenState
import az.pdfcreator.app.ui.MainViewModel
import az.pdfcreator.app.ui.theme.Brand
import az.pdfcreator.app.work.PdfWorker

@Composable
fun OptionsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val options by vm.options.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val defaultName = remember { vm.defaultFileName() }
    val suggestedName by vm.suggestedName.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(items.firstOrNull()?.uri) { vm.refreshSuggestedName() }

    var passwordOn by remember { mutableStateOf(options.password.isNotEmpty()) }
    var watermarkOn by remember { mutableStateOf(options.watermark.isNotEmpty()) }
    var showPassword by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var signaturePad by remember { mutableStateOf(false) }
    val hasSignature by vm.hasSignature.collectAsStateWithLifecycle()
    val signatureVersion by vm.signatureVersion.collectAsStateWithLifecycle()
    val signatureImage = rememberSignatureImage(signatureVersion)

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.startGeneration()
    }
    val start = {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT < 29) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) vm.startGeneration() else permissionLauncher.launch(needed.toTypedArray())
    }
    val pages = (items.size + options.imagesPerPage - 1) / options.imagesPerPage

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            BackHeader(stringResource(R.string.build_pdf_title), onBack)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 130.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // File name
                BrandCard(Modifier.fillMaxWidth()) {
                    CardLabel(stringResource(if (suggestedName != null && options.fileName.isEmpty()) R.string.smart_name else R.string.file_name))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            if (options.fileName.isEmpty()) {
                                Text(suggestedName ?: defaultName, style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            }
                            BasicTextField(
                                value = options.fileName,
                                onValueChange = { v -> vm.updateOptions { it.copy(fileName = v.take(120)) } },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(Brand.Coral),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            ".pdf", color = Brand.Navy, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Brand.Yellow).padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }

                // Page size
                BrandCard(Modifier.fillMaxWidth()) {
                    CardLabel(stringResource(R.string.page_size))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        PageSizeOption.entries.forEach { size ->
                            PageShape(size, size == options.pageSize) { vm.updateOptions { it.copy(pageSize = size) } }
                        }
                    }
                }

                // Per page + colour
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    BrandCard(Modifier.weight(1f).fillMaxHeight()) {
                        CardLabel(stringResource(R.string.images_per_page))
                        PillGroup(listOf(1, 2, 4), options.imagesPerPage, { "$it" }) { n -> vm.updateOptions { it.copy(imagesPerPage = n) } }
                    }
                    BrandCard(Modifier.weight(1f).fillMaxHeight()) {
                        CardLabel(stringResource(R.string.color))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ColorDot(ColorMode.ORIGINAL, options.colorMode) { vm.updateOptions { o -> o.copy(colorMode = it) } }
                            ColorDot(ColorMode.GRAYSCALE, options.colorMode) { vm.updateOptions { o -> o.copy(colorMode = it) } }
                            ColorDot(ColorMode.DOCUMENT, options.colorMode) { vm.updateOptions { o -> o.copy(colorMode = it) } }
                        }
                        Text(
                            stringResource(
                                when (options.colorMode) {
                                    ColorMode.ORIGINAL -> R.string.color_original
                                    ColorMode.GRAYSCALE -> R.string.color_gray
                                    ColorMode.DOCUMENT -> R.string.color_document
                                },
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Quality
                BrandCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { CardLabel(stringResource(R.string.quality)) }
                        Text(
                            if (options.maxSizeKb > 0 && options.estimateBytes(items.size) > options.maxSizeKb * 1024L) "≤ ${options.maxSizeKb / 1024} MB"
                            else "≈" + Formatter.formatShortFileSize(context, options.estimateBytes(items.size)),
                            color = Brand.Navy, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Brand.Yellow).padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    PillGroup(
                        QualityOption.entries, options.quality,
                        {
                            stringResource(
                                when (it) {
                                    QualityOption.LOW -> R.string.quality_low
                                    QualityOption.MEDIUM -> R.string.quality_medium
                                    QualityOption.HIGH -> R.string.quality_high
                                    QualityOption.MAX -> R.string.quality_max
                                },
                            )
                        },
                        selectedColor = Brand.Coral, selectedText = Brand.Navy,
                    ) { q -> vm.updateOptions { it.copy(quality = q) } }
                    Text(stringResource(R.string.quality_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CardLabel(stringResource(R.string.size_limit))
                    PillGroup(
                        listOf(0, 1024, 2048, 5120, 10240), options.maxSizeKb,
                        { if (it == 0) stringResource(R.string.size_limit_none) else "${it / 1024} MB" },
                        selectedColor = Brand.Yellow, selectedText = Brand.Navy,
                    ) { kb -> vm.updateOptions { it.copy(maxSizeKb = kb) } }
                    if (options.maxSizeKb > 0) {
                        Text(stringResource(R.string.size_limit_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // More options
                BrandCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { moreOpen = !moreOpen }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(if (moreOpen) R.string.less_options else R.string.more_options), fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                        Icon(if (moreOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    }
                    AnimatedVisibility(moreOpen) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val fitImage = options.pageSize == PageSizeOption.FIT_IMAGE && options.imagesPerPage == 1
                            if (!fitImage) {
                                CardLabel(stringResource(R.string.orientation))
                                PillGroup(
                                    OrientationOption.entries, options.orientation,
                                    {
                                        stringResource(
                                            when (it) {
                                                OrientationOption.AUTO -> R.string.orient_auto
                                                OrientationOption.PORTRAIT -> R.string.orient_portrait
                                                OrientationOption.LANDSCAPE -> R.string.orient_landscape
                                            },
                                        )
                                    },
                                ) { o -> vm.updateOptions { it.copy(orientation = o) } }
                                CardLabel(stringResource(R.string.image_scale))
                                PillGroup(
                                    ScaleMode.entries, options.scaleMode,
                                    { stringResource(if (it == ScaleMode.FIT) R.string.scale_fit else R.string.scale_fill) },
                                ) { m -> vm.updateOptions { it.copy(scaleMode = m) } }
                            }
                            CardLabel(stringResource(R.string.margin))
                            PillGroup(
                                MarginOption.entries, options.margin,
                                {
                                    stringResource(
                                        when (it) {
                                            MarginOption.NONE -> R.string.margin_none
                                            MarginOption.SMALL -> R.string.margin_small
                                            MarginOption.MEDIUM -> R.string.margin_medium
                                            MarginOption.LARGE -> R.string.margin_large
                                        },
                                    )
                                },
                            ) { m -> vm.updateOptions { it.copy(margin = m) } }
                        }
                    }
                }

                // Extras
                ExtrasRow(
                    pageNumbers = options.pageNumbers,
                    signature = options.signature,
                    onSignature = { v ->
                        if (v && !hasSignature) signaturePad = true
                        else vm.updateOptions { it.copy(signature = v) }
                    },
                    watermark = watermarkOn,
                    password = passwordOn,
                    onPageNumbers = { v -> vm.updateOptions { it.copy(pageNumbers = v) } },
                    searchable = options.searchable,
                    onSearchable = { v -> vm.updateOptions { it.copy(searchable = v) } },
                    onWatermark = { v -> watermarkOn = v; if (!v) vm.updateOptions { it.copy(watermark = "") } },
                    onPassword = { v -> passwordOn = v; passwordError = false; if (!v) vm.updateOptions { it.copy(password = "") } },
                )
                if (options.searchable) {
                    Text(stringResource(R.string.searchable_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                }
                if (options.signature && hasSignature) {
                    BrandCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.weight(1f).height(70.dp).clip(RoundedCornerShape(14.dp)).background(Color.White)
                                    .border(1.dp, Brand.Line, RoundedCornerShape(14.dp)).padding(8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                signatureImage?.let {
                                    androidx.compose.foundation.Image(
                                        it, null,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                                            if (options.signatureInk == SignatureInk.BLUE) Color(0xFF1A3A8F) else Color(0xFF14141E),
                                        ),
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            TextButton(onClick = { signaturePad = true }) { Text(stringResource(R.string.signature_change), fontWeight = FontWeight.Bold) }
                        }
                        CardLabel(stringResource(R.string.sig_where))
                        PillGroup(
                            SignaturePages.entries, options.signaturePages,
                            { stringResource(if (it == SignaturePages.LAST) R.string.sig_pages_last else R.string.sig_pages_all) },
                        ) { v -> vm.updateOptions { it.copy(signaturePages = v) } }
                        CardLabel(stringResource(R.string.sig_position))
                        PillGroup(
                            listOf(SignaturePos.LEFT, SignaturePos.CENTER, SignaturePos.RIGHT), options.signaturePos,
                            {
                                stringResource(
                                    when (it) {
                                        SignaturePos.LEFT -> R.string.sig_pos_left
                                        SignaturePos.CENTER -> R.string.sig_pos_center
                                        SignaturePos.RIGHT -> R.string.sig_pos_right
                                    },
                                )
                            },
                        ) { v -> vm.updateOptions { it.copy(signaturePos = v) } }
                        CardLabel(stringResource(R.string.sig_ink))
                        PillGroup(
                            SignatureInk.entries, options.signatureInk,
                            { stringResource(if (it == SignatureInk.BLUE) R.string.ink_blue else R.string.ink_black) },
                        ) { v -> vm.updateOptions { it.copy(signatureInk = v) } }
                    }
                }
                if (watermarkOn) {
                    BrandCard(Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = options.watermark,
                            onValueChange = { v -> vm.updateOptions { it.copy(watermark = v.take(40)) } },
                            label = { Text(stringResource(R.string.watermark)) },
                            placeholder = { Text(stringResource(R.string.watermark_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = brandFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (passwordOn) {
                    BrandCard(Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = options.password,
                            onValueChange = { v ->
                                // PDF passwords must be Latin-1 for every viewer; keep to plain ASCII.
                                val clean = v.filter { it.code in 33..126 }.take(32)
                                passwordError = false
                                vm.updateOptions { it.copy(password = clean) }
                            },
                            label = { Text(stringResource(R.string.password)) },
                            leadingIcon = { Icon(Icons.Default.Lock, null) },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            isError = passwordError,
                            supportingText = { Text(stringResource(if (passwordError) R.string.password_short else R.string.password_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = brandFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        BigActionButton(
            text = pluralStringResource(R.plurals.create_n_pages, pages, pages),
            enabled = items.isNotEmpty(),
            onClick = {
                if (passwordOn && options.password.length < 4) {
                    passwordError = true
                } else {
                    if (!passwordOn) vm.updateOptions { it.copy(password = "") }
                    if (!watermarkOn) vm.updateOptions { it.copy(watermark = "") }
                    start()
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        )
    }

    GenerationDialogs(vm)

    if (signaturePad) {
        SignaturePadDialog(
            onDismiss = { signaturePad = false },
            onSave = {
                vm.saveSignature(it)
                vm.updateOptions { o -> o.copy(signature = true) }
                signaturePad = false
            },
        )
    }
}

/** Progress while the PDF is being created, and the error dialog. Shared by all create flows. */
@Composable
fun GenerationDialogs(vm: MainViewModel) {
    val gen by vm.gen.collectAsStateWithLifecycle()
    when (val s = gen) {
        is GenState.Running -> ProgressDialog(s.done, s.total, s.pass, onCancel = vm::cancelGeneration)
        is GenState.Failed -> AlertDialog(
            onDismissRequest = vm::resetGeneration,
            title = { Text(stringResource(R.string.error_title), fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    when (s.error) {
                        PdfWorker.ERROR_NO_IMAGES -> stringResource(R.string.error_no_images)
                        PdfWorker.ERROR_MEMORY -> stringResource(R.string.error_memory)
                        else -> stringResource(R.string.error_generic, s.error)
                    },
                )
            },
            confirmButton = { TextButton(onClick = vm::resetGeneration) { Text(stringResource(R.string.ok)) } },
        )
        else -> Unit
    }
}

@Composable
private fun brandFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Brand.Coral,
    focusedLabelColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = Brand.Coral,
)


@Composable
private fun PageShape(size: PageSizeOption, selected: Boolean, onClick: () -> Unit) {
    val (w, h) = when (size) {
        PageSizeOption.A4 -> 44 to 62
        PageSizeOption.LETTER -> 48 to 62
        PageSizeOption.LEGAL -> 40 to 66
        PageSizeOption.A5 -> 34 to 48
        PageSizeOption.A3 -> 50 to 70
        PageSizeOption.FIT_IMAGE -> 56 to 42
    }
    val label = when (size) {
        PageSizeOption.A4 -> "A4"
        PageSizeOption.LETTER -> "Letter"
        PageSizeOption.LEGAL -> "Legal"
        PageSizeOption.A5 -> "A5"
        PageSizeOption.A3 -> "A3"
        PageSizeOption.FIT_IMAGE -> stringResource(R.string.size_fit_image)
    }
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(w.dp, h.dp).clip(RoundedCornerShape(8.dp))
                .background(if (selected) Brand.Coral else MaterialTheme.colorScheme.surfaceVariant)
                .then(if (selected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (size == PageSizeOption.FIT_IMAGE) {
                Icon(Icons.Default.Image, null, tint = if (selected) Brand.Navy else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
        Text(
            label,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun <T> PillGroup(
    values: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    selectedText: Color = MaterialTheme.colorScheme.onPrimary,
    onSelect: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEach { v ->
            val on = v == selected
            Surface(
                onClick = { onSelect(v) },
                shape = RoundedCornerShape(14.dp),
                color = if (on) selectedColor else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (on) selectedText else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).height(46.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        label(v),
                        fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorDot(mode: ColorMode, selected: ColorMode, onSelect: (ColorMode) -> Unit) {
    val on = mode == selected
    val label = stringResource(
        when (mode) {
            ColorMode.ORIGINAL -> R.string.color_original
            ColorMode.GRAYSCALE -> R.string.color_gray
            ColorMode.DOCUMENT -> R.string.color_document
        },
    )
    Surface(
        onClick = { onSelect(mode) },
        shape = CircleShape,
        color = when (mode) {
            ColorMode.ORIGINAL -> Color(0xFFF4A26B)
            ColorMode.GRAYSCALE -> Color(0xFF9A9AAE)
            ColorMode.DOCUMENT -> Color.White
        },
        border = when {
            on -> BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
            mode == ColorMode.DOCUMENT -> BorderStroke(2.dp, Brand.Line)
            else -> null
        },
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (mode == ColorMode.DOCUMENT) Text("A", color = Brand.Navy, fontWeight = FontWeight.Black)
            if (on && mode != ColorMode.DOCUMENT) Icon(Icons.Default.Check, label, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtrasRow(
    pageNumbers: Boolean,
    searchable: Boolean,
    onSearchable: (Boolean) -> Unit,
    signature: Boolean,
    onSignature: (Boolean) -> Unit,
    watermark: Boolean,
    password: Boolean,
    onPageNumbers: (Boolean) -> Unit,
    onWatermark: (Boolean) -> Unit,
    onPassword: (Boolean) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ToggleChip(stringResource(R.string.searchable), searchable, onSearchable)
        ToggleChip(stringResource(R.string.signature), signature, onSignature)
        ToggleChip(stringResource(R.string.page_numbers_short), pageNumbers, onPageNumbers)
        ToggleChip(stringResource(R.string.add_watermark), watermark, onWatermark)
        ToggleChip(stringResource(R.string.add_password), password, onPassword)
    }
}

@Composable
private fun ToggleChip(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        onClick = { onChange(!on) },
        shape = RoundedCornerShape(22.dp),
        color = if (on) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        border = if (on) null else BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.height(46.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (on) Icons.Default.Check else Icons.Default.Add, null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ProgressDialog(done: Int, total: Int, pass: Int, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.creating_pdf), fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else done.toFloat() / total },
                    color = Brand.Coral,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                )
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.progress_images, done, total), fontWeight = FontWeight.Bold)
                if (pass > 1) {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.compress_pass, pass), color = Brand.Coral, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.progress_background_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}
