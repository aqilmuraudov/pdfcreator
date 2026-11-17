package az.pdfcreator.app.ui.screens

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.pdfcreator.app.R
import az.pdfcreator.app.model.ColorMode
import az.pdfcreator.app.ui.MainViewModel
import az.pdfcreator.app.ui.theme.Brand
import coil.compose.AsyncImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/** ID card copy: front + back on one A4 page at real size (ASAN / bank style photocopy). */
@Composable
fun IdCardScreen(vm: MainViewModel, onBack: () -> Unit) {
    val front by vm.idFront.collectAsStateWithLifecycle()
    val back by vm.idBack.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingFront by rememberSaveable { mutableStateOf(true) }
    var large by rememberSaveable { mutableStateOf(false) }
    var gray by rememberSaveable { mutableStateOf(false) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setIdSide(pendingFront, uri)
    }
    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(res.data)?.pages?.firstOrNull()?.imageUri
                ?.let { vm.setIdSide(pendingFront, it) }
        }
    }
    val scan: (Boolean) -> Unit = { isFront ->
        pendingFront = isFront
        val activity = context.findActivity()
        if (activity != null) {
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(1)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
            GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                .addOnSuccessListener { scanner.launch(IntentSenderRequest.Builder(it).build()) }
                .addOnFailureListener { Toast.makeText(context, R.string.scanner_unavailable, Toast.LENGTH_LONG).show() }
        }
    }
    val pickGallery: (Boolean) -> Unit = { isFront ->
        pendingFront = isFront
        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            BackHeader(stringResource(R.string.tool_id_card), onBack)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CardSlot(stringResource(R.string.id_front), front, { scan(true) }, { pickGallery(true) }, { vm.setIdSide(true, null) })
                CardSlot(stringResource(R.string.id_back), back, { scan(false) }, { pickGallery(false) }, { vm.setIdSide(false, null) })

                BrandCard(Modifier.fillMaxWidth()) {
                    CardLabel(stringResource(R.string.id_size))
                    TwoPills(stringResource(R.string.id_real), stringResource(R.string.id_large), large) { large = it }
                    Text(stringResource(R.string.id_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CardLabel(stringResource(R.string.color))
                    TwoPills(stringResource(R.string.color_original), stringResource(R.string.color_gray), gray) { gray = it }
                }

                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brand.Navy).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Brand.Yellow), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Lightbulb, null, tint = Brand.Navy, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.id_tip), color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        BigActionButton(
            text = stringResource(R.string.id_create),
            enabled = front != null || back != null,
            onClick = { vm.startIdCard(if (large) 1.5f else 1f, if (gray) ColorMode.GRAYSCALE else ColorMode.ORIGINAL) },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        )
    }
    GenerationDialogs(vm)
}

@Composable
private fun CardSlot(title: String, uri: Uri?, onScan: () -> Unit, onGallery: () -> Unit, onClear: () -> Unit) {
    BrandCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (uri != null) {
                Surface(onClick = onClear, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, stringResource(R.string.delete), modifier = Modifier.size(18.dp)) }
                }
            }
        }
        // Card-shaped preview area (85.6 x 54 mm).
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.586f).clip(RoundedCornerShape(16.dp))
                .background(if (uri == null) Brand.Lavender else Color.Black)
                .then(if (uri == null) Modifier.border(2.dp, Brand.Line, RoundedCornerShape(16.dp)) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (uri != null) {
                AsyncImage(model = uri, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Default.Badge, null, tint = Brand.Line, modifier = Modifier.size(64.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SlotButton(Icons.Default.DocumentScanner, stringResource(R.string.id_scan), true, Modifier.weight(1f), onScan)
            SlotButton(Icons.Default.PhotoLibrary, stringResource(R.string.src_gallery), false, Modifier.weight(1f), onGallery)
        }
    }
}

@Composable
private fun SlotButton(icon: ImageVector, label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (primary) Brand.Coral else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = Brand.Navy.takeIf { primary } ?: MaterialTheme.colorScheme.onSurface,
        modifier = modifier.height(50.dp),
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun TwoPills(first: String, second: String, secondSelected: Boolean, onChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(false to first, true to second).forEach { (value, label) ->
            val on = value == secondSelected
            Surface(
                onClick = { onChange(value) },
                shape = RoundedCornerShape(14.dp),
                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).height(46.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
