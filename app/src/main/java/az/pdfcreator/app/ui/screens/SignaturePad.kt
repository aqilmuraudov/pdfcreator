package az.pdfcreator.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.pdfcreator.app.R
import az.pdfcreator.app.ui.MainViewModel
import az.pdfcreator.app.ui.theme.Brand
import az.pdfcreator.app.work.PdfWorker
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Full-screen pad: the user signs with a finger; the result is a tightly cropped transparent bitmap. */
@Composable
fun SignaturePadDialog(onDismiss: () -> Unit, onSave: (Bitmap) -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val strokePx = with(LocalDensity.current) { 3.2.dp.toPx() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.signature_draw_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
                }
                Text(stringResource(R.string.signature_draw_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)

                Box(
                    Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp)).background(Color.White)
                        .border(2.dp, Brand.Line, RoundedCornerShape(24.dp))
                        .onSizeChanged { size = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { current = listOf(it) },
                                onDrag = { change, _ -> current = current + change.position },
                                onDragEnd = { if (current.size > 1) strokes.add(current); current = emptyList() },
                                onDragCancel = { current = emptyList() },
                            )
                        },
                ) {
                    // Baseline to sign on.
                    Box(Modifier.align(Alignment.BottomCenter).padding(start = 28.dp, end = 28.dp, bottom = 70.dp).fillMaxWidth().height(2.dp).background(Brand.Line))
                    Canvas(Modifier.fillMaxSize()) {
                        (strokes + listOf(current)).forEach { pts ->
                            if (pts.size > 1) drawPath(smoothPath(pts), Brand.Navy, style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        onClick = { strokes.clear(); current = emptyList() },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text(stringResource(R.string.signature_clear), fontWeight = FontWeight.ExtraBold) }
                    }
                    Surface(
                        onClick = { renderSignature(strokes, size, strokePx)?.let { onSave(it) } },
                        enabled = strokes.isNotEmpty(),
                        shape = RoundedCornerShape(20.dp),
                        color = if (strokes.isNotEmpty()) Brand.Navy else Brand.Line,
                        contentColor = Color.White,
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text(stringResource(R.string.signature_save), fontWeight = FontWeight.ExtraBold) }
                    }
                }
            }
        }
    }
}

private fun smoothPath(pts: List<Offset>): Path = Path().apply {
    moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) {
        val prev = pts[i - 1]
        val mid = Offset((prev.x + pts[i].x) / 2f, (prev.y + pts[i].y) / 2f)
        quadraticBezierTo(prev.x, prev.y, mid.x, mid.y)
    }
    lineTo(pts.last().x, pts.last().y)
}

/** Draws the strokes (black, alpha = ink) into a bitmap cropped to the signature with a small margin. */
private fun renderSignature(strokes: List<List<Offset>>, size: IntSize, strokePx: Float): Bitmap? {
    if (strokes.isEmpty() || size.width == 0) return null
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = 0f; var maxY = 0f
    strokes.flatten().forEach { minX = min(minX, it.x); minY = min(minY, it.y); maxX = max(maxX, it.x); maxY = max(maxY, it.y) }
    val pad = strokePx * 2
    val left = max(0f, minX - pad); val top = max(0f, minY - pad)
    val w = (min(size.width.toFloat(), maxX + pad) - left).roundToInt().coerceAtLeast(1)
    val h = (min(size.height.toFloat(), maxY + pad) - top).roundToInt().coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    strokes.forEach { pts ->
        val path = android.graphics.Path()
        path.moveTo(pts[0].x - left, pts[0].y - top)
        for (i in 1 until pts.size) {
            val p = pts[i - 1]
            path.quadTo(p.x - left, p.y - top, (p.x + pts[i].x) / 2f - left, (p.y + pts[i].y) / 2f - top)
        }
        path.lineTo(pts.last().x - left, pts.last().y - top)
        canvas.drawPath(path, paint)
    }
    return bmp
}

/** Loads the saved signature for previews; reloads when [version] changes. */
@Composable
fun rememberSignatureImage(version: Int): ImageBitmap? {
    val context = LocalContext.current
    return remember(version) {
        val f = PdfWorker.signatureFile(context)
        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
    }
}

/** View / change / delete the saved signature. */
@Composable
fun SignatureManagerDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val version by vm.signatureVersion.collectAsStateWithLifecycle()
    val has by vm.hasSignature.collectAsStateWithLifecycle()
    var drawing by remember { mutableStateOf(!has) }
    val image = rememberSignatureImage(version)

    if (drawing) {
        SignaturePadDialog(
            onDismiss = { if (has) drawing = false else onDismiss() },
            onSave = { vm.saveSignature(it); drawing = false },
        )
        return
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.my_signature), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Box(
                    Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(18.dp)).background(Color.White).border(1.dp, Brand.Line, RoundedCornerShape(18.dp)).padding(14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (image != null) Image(image, null, contentScale = ContentScale.Fit, colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Brand.Navy), modifier = Modifier.fillMaxSize())
                    else Text(stringResource(R.string.signature_none), color = Brand.Muted)
                }
                Text(stringResource(R.string.signature_saved_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        onClick = { vm.deleteSignature(); onDismiss() },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(52.dp),
                    ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error) } }
                    Surface(
                        onClick = { drawing = true },
                        shape = RoundedCornerShape(18.dp),
                        color = Brand.Navy,
                        contentColor = Color.White,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Draw, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.signature_change), fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}
