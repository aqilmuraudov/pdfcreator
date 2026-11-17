package az.pdfcreator.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.pdfcreator.app.R
import az.pdfcreator.app.ui.theme.Brand
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val bg by animateColorAsState(
        when (pager.currentPage) { 0 -> Brand.Coral; 1 -> Brand.Yellow; else -> Brand.Navy },
        label = "onbBg",
    )

    Box(Modifier.fillMaxSize().background(bg)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> PageOne()
                1 -> PageTwo()
                else -> PageThree()
            }
        }

        // Skip button (top) on the first two pages.
        if (pager.currentPage < 2) {
            Surface(
                onClick = onFinish,
                shape = RoundedCornerShape(22.dp),
                color = Color.White.copy(alpha = 0.4f),
                contentColor = Brand.Navy,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 10.dp, end = 20.dp),
            ) {
                Text(
                    stringResource(R.string.skip),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                )
            }
        }

        // Bottom: dots + button
        val last = pager.currentPage == 2
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { i ->
                    val w by animateDpAsState(if (i == pager.currentPage) 28.dp else 8.dp, label = "dot")
                    Box(
                        Modifier.height(8.dp).width(w).clip(CircleShape)
                            .background(
                                when {
                                    last && i == 2 -> Color.White
                                    last -> Color(0xFF4A4A6A)
                                    i == pager.currentPage -> Brand.Navy
                                    else -> Brand.Navy.copy(alpha = 0.2f)
                                },
                            ),
                    )
                }
            }
            Surface(
                onClick = { if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                shape = RoundedCornerShape(24.dp),
                color = if (last) Brand.Coral else Brand.Navy,
                contentColor = if (last) Brand.Navy else Color.White,
                modifier = Modifier.fillMaxWidth().height(62.dp),
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(if (last) R.string.onb_start else R.string.next),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    if (!last) {
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

/** Page 1: photos turn into a PDF. */
@Composable
private fun PageOne() {
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val cx = maxWidth / 2
            Box(Modifier.offset(x = (-40).dp, y = 110.dp).size(200.dp).clip(CircleShape).background(Brand.Yellow))
            Card(cx - 135.dp, 140.dp, -14f, Color(0xFF7C8DF0))
            Card(cx - 55.dp, 120.dp, 4f, Color(0xFF5FC3A4))
            Box(
                Modifier.offset(x = cx + 5.dp, y = 170.dp).size(width = 128.dp, height = 166.dp).rotate(12f)
                    .shadow(14.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Brand.Navy),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Text("PDF", color = Brand.Coral, fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 18.dp))
            }
        }
        BottomPanel(Color.White) {
            Text(stringResource(R.string.onb1_title), fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black, color = Brand.Navy)
            Text(stringResource(R.string.onb1_text), fontSize = 16.sp, lineHeight = 24.sp, color = Brand.Muted)
        }
    }
}

@Composable
private fun Card(x: androidx.compose.ui.unit.Dp, y: androidx.compose.ui.unit.Dp, angle: Float, color: Color) {
    Box(
        Modifier.offset(x = x, y = y).size(width = 120.dp, height = 154.dp).rotate(angle)
            .shadow(12.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(color),
    )
}

/** Page 2: three steps. */
@Composable
private fun PageTwo() {
    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.onb2_label),
            fontWeight = FontWeight.ExtraBold,
            color = Brand.Navy,
            modifier = Modifier.statusBarsPadding().padding(start = 24.dp, top = 22.dp),
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Step(1, R.string.onb_step1_title, R.string.onb_step1_text, -2f, Brand.Navy, Color.White)
            Step(2, R.string.onb_step2_title, R.string.onb_step2_text, 1.5f, Brand.Mist, Brand.Navy)
            Step(3, R.string.onb_step3_title, R.string.onb_step3_text, -1f, Brand.Coral, Brand.Navy)
        }
        BottomPanel(Color.White) {
            Text(stringResource(R.string.onb2_title), fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black, color = Brand.Navy)
        }
    }
}

@Composable
private fun Step(n: Int, title: Int, text: Int, tilt: Float, badge: Color, badgeText: Color) {
    Row(
        Modifier.fillMaxWidth().rotate(tilt).clip(RoundedCornerShape(28.dp)).background(Color.White).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).background(badge), contentAlignment = Alignment.Center) {
            Text("$n", fontSize = 28.sp, fontWeight = FontWeight.Black, color = badgeText)
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(title), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Brand.Navy)
            Text(stringResource(text), fontSize = 14.sp, lineHeight = 19.sp, color = Brand.Muted)
        }
    }
}

/** Page 3: fast, private, offline. */
@Composable
private fun PageThree() {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Pill(Icons.Default.Bolt, R.string.onb3_fast, Brand.Coral, -3f, Alignment.Start)
        Pill(Icons.Default.Lock, R.string.onb3_private, Brand.Yellow, 2.5f, Alignment.End)
        Pill(Icons.Default.WifiOff, R.string.onb3_offline, Color.White, -1.5f, Alignment.Start, startPad = 30)
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.onb3_text), fontSize = 16.sp, lineHeight = 24.sp, color = Color(0xFFC9C8DE))
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Pill(
    icon: ImageVector, label: Int, color: Color, tilt: Float, align: Alignment.Horizontal, startPad: Int = 0,
) {
    Row(
        Modifier.align(align).padding(start = startPad.dp).rotate(tilt).clip(RoundedCornerShape(26.dp)).background(color)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Brand.Navy, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(label), fontSize = 30.sp, fontWeight = FontWeight.Black, color = Brand.Navy)
    }
}

@Composable
private fun BottomPanel(color: Color, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)).background(color)
            .padding(start = 24.dp, end = 24.dp, top = 54.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}
