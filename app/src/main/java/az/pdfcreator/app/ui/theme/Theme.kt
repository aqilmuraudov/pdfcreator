@file:OptIn(ExperimentalTextApi::class)

package az.pdfcreator.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import az.pdfcreator.app.R

/** Design "B · Canlı": coral + navy + yellow, big rounded cards. */
object Brand {
    val Coral = Color(0xFFFF5A36)
    val Navy = Color(0xFF1B1B3A)
    val Yellow = Color(0xFFFFC53D)
    val Lavender = Color(0xFFF6F5FB)
    val Mist = Color(0xFFEEEDF6)
    val Muted = Color(0xFF5A5A78)
    val Line = Color(0xFFD9D8E6)

    /** Soft tile colours for thumbnails and recent PDFs. */
    val Tiles = listOf(
        Color(0xFF7C8DF0), Color(0xFF5FC3A4), Color(0xFFC78DE8),
        Color(0xFFF4A26B), Color(0xFF6FB7E8), Color(0xFFE88DA8),
    )
}

private fun nunito(weight: Int) =
    Font(R.font.nunito, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Nunito = FontFamily(
    nunito(400), nunito(500), nunito(600), nunito(700), nunito(800), nunito(900),
)

private fun Typography.withFont(f: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = f)
    return copy(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}

private val Light = lightColorScheme(
    primary = Brand.Navy,
    onPrimary = Color.White,
    primaryContainer = Brand.Mist,
    onPrimaryContainer = Brand.Navy,
    secondary = Brand.Coral,
    onSecondary = Brand.Navy,
    tertiary = Brand.Yellow,
    onTertiary = Brand.Navy,
    background = Brand.Lavender,
    onBackground = Brand.Navy,
    surface = Color.White,
    onSurface = Brand.Navy,
    surfaceVariant = Brand.Mist,
    onSurfaceVariant = Brand.Muted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Brand.Mist,
    outline = Brand.Line,
    outlineVariant = Brand.Line,
    error = Color(0xFFC62828),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFECEBFF),
    onPrimary = Brand.Navy,
    primaryContainer = Color(0xFF2A2A4A),
    onPrimaryContainer = Color(0xFFECEBFF),
    secondary = Brand.Coral,
    onSecondary = Brand.Navy,
    tertiary = Brand.Yellow,
    onTertiary = Brand.Navy,
    background = Color(0xFF121226),
    onBackground = Color(0xFFF2F1FA),
    surface = Color(0xFF1E1E3A),
    onSurface = Color(0xFFF2F1FA),
    surfaceVariant = Color(0xFF2A2A4A),
    onSurfaceVariant = Color(0xFFB9B8D3),
    surfaceContainerLowest = Color(0xFF1E1E3A),
    surfaceContainerLow = Color(0xFF1E1E3A),
    surfaceContainer = Color(0xFF1E1E3A),
    surfaceContainerHigh = Color(0xFF26264A),
    surfaceContainerHighest = Color(0xFF2A2A4A),
    outline = Color(0xFF45456A),
    outlineVariant = Color(0xFF45456A),
    error = Color(0xFFFF8A80),
)

@Composable
fun PdfCreatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Typography().withFont(Nunito),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(10.dp),
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(32.dp),
        ),
        content = content,
    )
}
