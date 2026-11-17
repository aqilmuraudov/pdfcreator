package az.pdfcreator.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.pdfcreator.app.R
import az.pdfcreator.app.model.PageSizeOption
import az.pdfcreator.app.ui.screens.AboutScreen
import az.pdfcreator.app.ui.screens.EditorScreen
import az.pdfcreator.app.ui.screens.ExtractedTextDialog
import az.pdfcreator.app.ui.screens.PdfToolsScreen
import az.pdfcreator.app.ui.screens.HistoryScreen
import az.pdfcreator.app.ui.screens.HomeScreen
import az.pdfcreator.app.ui.screens.IdCardScreen
import az.pdfcreator.app.ui.screens.OnboardingScreen
import az.pdfcreator.app.ui.screens.OptionsScreen
import az.pdfcreator.app.ui.screens.ResultScreen
import az.pdfcreator.app.ui.screens.SettingsScreen

enum class Screen { HOME, EDITOR, OPTIONS, RESULT, HISTORY, SETTINGS, ABOUT, ID_CARD, PDF_TOOLS }

@Composable
fun AppRoot(vm: MainViewModel) {
    val onboardingDone by vm.onboardingDone.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(if (vm.items.value.isNotEmpty()) Screen.EDITOR else Screen.HOME) }
    val gen by vm.gen.collectAsStateWithLifecycle()
    val event by vm.events.collectAsStateWithLifecycle()
    val options by vm.options.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (!onboardingDone) {
        OnboardingScreen(onFinish = { vm.finishOnboarding(); screen = if (vm.items.value.isNotEmpty()) Screen.EDITOR else Screen.HOME })
        return
    }

    LaunchedEffect(gen) {
        if (gen is GenState.Success) screen = Screen.RESULT
    }
    LaunchedEffect(event) {
        when (val e = event) {
            is UiEvent.PdfImportFailed -> Toast.makeText(
                context,
                if (e.locked) R.string.import_pdf_locked else R.string.import_pdf_failed,
                Toast.LENGTH_LONG,
            ).show()
            // Images arriving from "Share → PDF Creator" while elsewhere: open the editor.
            is UiEvent.Added -> if (screen == Screen.HOME || screen == Screen.HISTORY || screen == Screen.SETTINGS) screen = Screen.EDITOR
            is UiEvent.Straightened -> Toast.makeText(
                context,
                if (e.changed) R.string.straighten_done else R.string.straighten_none,
                Toast.LENGTH_SHORT,
            ).show()
            null -> Unit
        }
        if (event != null) vm.consumeEvent()
    }

    fun leaveResult(to: Screen) {
        vm.resetGeneration()
        screen = to
    }

    BackHandler(enabled = screen != Screen.HOME) {
        screen = when (screen) {
            Screen.OPTIONS -> Screen.EDITOR
            Screen.ABOUT -> Screen.SETTINGS
            Screen.RESULT -> { vm.resetGeneration(); Screen.HOME }
            else -> Screen.HOME
        }
    }

    ExtractedTextDialog(vm)

    when (screen) {
        Screen.HOME -> HomeScreen(
            vm = vm,
            onOpenEditor = { screen = Screen.EDITOR },
            onHistory = { vm.refreshHistory(); screen = Screen.HISTORY },
            onSettings = { screen = Screen.SETTINGS },
            onIdCard = { screen = Screen.ID_CARD },
            onPdfTools = { screen = Screen.PDF_TOOLS },
        )
        Screen.PDF_TOOLS -> PdfToolsScreen(vm = vm, onBack = { screen = Screen.HOME })
        Screen.ID_CARD -> IdCardScreen(vm = vm, onBack = { screen = Screen.HOME })
        Screen.EDITOR -> EditorScreen(vm = vm, onBack = { screen = Screen.HOME }, onCreate = { screen = Screen.OPTIONS })
        Screen.OPTIONS -> OptionsScreen(vm = vm, onBack = { screen = Screen.EDITOR })
        Screen.RESULT -> {
            val s = gen
            if (s is GenState.Success) {
                ResultScreen(
                    result = s,
                    formatLabel = when (options.pageSize) {
                        PageSizeOption.FIT_IMAGE -> context.getString(R.string.size_fit_image)
                        PageSizeOption.LETTER -> "Letter"
                        PageSizeOption.LEGAL -> "Legal"
                        else -> options.pageSize.name
                    },
                    onNewPdf = { vm.clear(); vm.clearIdCard(); leaveResult(Screen.HOME) },
                    onClose = { leaveResult(Screen.HOME) },
                    onBackToPages = { leaveResult(Screen.EDITOR) },
                )
            } else {
                LaunchedEffect(Unit) { screen = Screen.HOME }
            }
        }
        Screen.HISTORY -> HistoryScreen(
            vm = vm,
            onBack = { screen = Screen.HOME },
            onEdit = { uri -> vm.editorOpen(uri); screen = Screen.PDF_TOOLS },
        )
        Screen.SETTINGS -> SettingsScreen(
            vm = vm,
            onBack = { screen = Screen.HOME },
            onAbout = { screen = Screen.ABOUT },
        )
        Screen.ABOUT -> AboutScreen(onBack = { screen = Screen.SETTINGS })
    }
}
