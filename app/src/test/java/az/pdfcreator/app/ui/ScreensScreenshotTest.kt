package az.pdfcreator.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import az.pdfcreator.app.data.HistoryRepository
import az.pdfcreator.app.model.HistoryEntry
import az.pdfcreator.app.ui.screens.EditorScreen
import az.pdfcreator.app.ui.screens.HistoryScreen
import az.pdfcreator.app.ui.screens.HomeScreen
import az.pdfcreator.app.ui.screens.OnboardingScreen
import az.pdfcreator.app.ui.screens.OptionsScreen
import az.pdfcreator.app.ui.screens.ResultScreen
import az.pdfcreator.app.ui.screens.SettingsScreen
import az.pdfcreator.app.ui.theme.PdfCreatorTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders every screen to PNG so the design can be reviewed without a device. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "b+az-w392dp-h850dp-xxhdpi")
class ScreensScreenshotTest {

    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val out = "build/screens"

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
    }

    private fun image(name: String, w: Int, h: Int, color: Int): Uri {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(color)
        val f = File(app.cacheDir, "$name.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        return Uri.fromFile(f)
    }

    private fun vmWithData(): MainViewModel {
        val dir = File(app.filesDir, "pdfs").apply { mkdirs() }
        val repo = HistoryRepository(app)
        listOf("Müqavilə_noyabr.pdf" to 12, "Qəbzlər_oktyabr.pdf" to 8, "Dərs_qeydləri.pdf" to 24).forEachIndexed { i, (n, p) ->
            val f = File(dir, n).apply { writeText("x") }
            repo.add(HistoryEntry(n, Uri.fromFile(f).toString(), f.absolutePath, 1_400_000L + i * 900_000, p, System.currentTimeMillis() - i * 86_400_000L, i == 1))
        }
        val vm = MainViewModel(app)
        val colors = listOf(0xFF7C8DF0, 0xFFEEEEEE, 0xFFF4A26B, 0xFF5FC3A4, 0xFFC78DE8, 0xFF6FB7E8)
        vm.addUris(colors.mapIndexed { i, c -> image("img$i", if (i % 2 == 0) 900 else 1200, 1200, c.toInt()) })
        vm.refreshHistory()
        vm.consumeEvent()
        return vm
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/$name.png")
    }

    @Test fun onboarding() {
        compose.setContent { PdfCreatorTheme { OnboardingScreen(onFinish = {}) } }
        shot("01_onboarding_1")
        compose.onRoot().performTouchInput { swipeLeft() }
        shot("02_onboarding_2")
        compose.onRoot().performTouchInput { swipeLeft() }
        shot("03_onboarding_3")
    }

    @Test fun home() {
        val vm = vmWithData()
        compose.setContent { PdfCreatorTheme { HomeScreen(vm, {}, {}, {}) } }
        shot("04_home")
    }

    @Test fun editor() {
        val vm = vmWithData()
        compose.setContent { PdfCreatorTheme { EditorScreen(vm, {}, {}) } }
        shot("05_editor")
    }

    @Test fun options() {
        val vm = vmWithData()
        compose.setContent { PdfCreatorTheme { OptionsScreen(vm, {}) } }
        shot("06_options")
    }

    @Test @Config(qualifiers = "b+az-w392dp-h850dp-night-xxhdpi") fun optionsDark() {
        val vm = vmWithData()
        compose.setContent {
            PdfCreatorTheme {
                androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) { OptionsScreen(vm, {}) }
            }
        }
        shot("11_options_dark")
    }

    @Test fun result() {
        compose.setContent {
            PdfCreatorTheme {
                ResultScreen(
                    GenState.Success(Uri.parse("content://x/1"), "Müqavilə_noyabr.pdf", 12, 3_250_000, 0, true),
                    "A4", {}, {}, {},
                )
            }
        }
        shot("07_result")
    }

    @Test fun history() {
        val vm = vmWithData()
        compose.setContent { PdfCreatorTheme { HistoryScreen(vm, {}) } }
        shot("08_history")
    }

    @Test fun settings() {
        val vm = vmWithData()
        compose.setContent { PdfCreatorTheme { SettingsScreen(vm, {}, {}) } }
        shot("09_settings")
    }

    @Test @Config(qualifiers = "b+az-w392dp-h850dp-night-xxhdpi") fun homeDark() {
        val vm = vmWithData()
        compose.setContent {
            PdfCreatorTheme {
                androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) { HomeScreen(vm, {}, {}, {}) }
            }
        }
        shot("10_home_dark")
    }

    @Test fun idCard() {
        val vm = vmWithData()
        vm.setIdSide(true, image("front", 1012, 638, 0xFF6FB7E8.toInt()))
        compose.setContent { PdfCreatorTheme { az.pdfcreator.app.ui.screens.IdCardScreen(vm) {} } }
        shot("14_idcard")
    }

    @Test fun optionsBottom() {
        val vm = vmWithData()
        az.pdfcreator.app.work.PdfWorker.signatureFile(app).outputStream().use { o ->
            val b = Bitmap.createBitmap(500, 180, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 8f; style = android.graphics.Paint.Style.STROKE }
            c.drawPath(android.graphics.Path().apply { moveTo(20f, 140f); cubicTo(100f, 10f, 160f, 170f, 250f, 80f); cubicTo(320f, 20f, 380f, 160f, 480f, 100f) }, p)
            b.compress(Bitmap.CompressFormat.PNG, 100, o)
        }
        val vm2 = MainViewModel(app)
        vm2.addUris(vm.items.value.map { it.uri })
        vm2.updateOptions { it.copy(signature = true, maxSizeKb = 2048) }
        compose.setContent { PdfCreatorTheme { OptionsScreen(vm2, {}) } }
        compose.onRoot().performTouchInput { swipeUp(durationMillis = 300) }
        compose.onRoot().performTouchInput { swipeUp(durationMillis = 300) }
        shot("15_options_bottom")
    }

    @Test fun pdfToolsEmpty() {
        val vm = vmWithData()
        compose.setContent { PdfCreatorTheme { az.pdfcreator.app.ui.screens.PdfToolsScreen(vm) {} } }
        shot("16_pdftools")
    }

    @Test fun homeTools() {
        val vm = vmWithData()
        compose.setContent { PdfCreatorTheme { HomeScreen(vm, {}, {}, {}) } }
        compose.onRoot().performTouchInput { swipeUp(durationMillis = 400) }
        shot("17_home_tools")
    }

    @Test fun about() {
        compose.setContent { PdfCreatorTheme { az.pdfcreator.app.ui.screens.AboutScreen {} } }
        shot("12_about")
        compose.onRoot().performTouchInput { swipeUp() }
        shot("13_about_bottom")
    }

    @Suppress("unused") private val keep = Color.WHITE
}
