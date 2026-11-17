package az.pdfcreator.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.content.IntentCompat
import az.pdfcreator.app.ui.AppRoot
import az.pdfcreator.app.ui.MainViewModel
import az.pdfcreator.app.ui.theme.PdfCreatorTheme

class MainActivity : AppCompatActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleShareIntent(intent)
        setContent {
            PdfCreatorTheme {
                // Surface supplies the right text colour (onBackground) in light and dark mode.
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppRoot(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    /** Images shared from the gallery or other apps ("Share → PDF Creator"). */
    private fun handleShareIntent(intent: Intent?) {
        intent ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        if (uris.isEmpty()) return
        val pdfs = uris.filter { contentResolver.getType(it) == "application/pdf" }
        vm.addUris(uris - pdfs.toSet())
        pdfs.forEach { vm.importPdf(it) }
        // Avoid re-adding on recreation.
        intent.action = Intent.ACTION_MAIN
    }
}
