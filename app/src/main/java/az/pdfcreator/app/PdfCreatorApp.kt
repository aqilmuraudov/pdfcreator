package az.pdfcreator.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import az.pdfcreator.app.data.Prefs
import az.pdfcreator.app.work.PdfWorker

class PdfCreatorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(Prefs(this).themeMode)
        PdfWorker.ensureChannel(this)
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext)
    }
}
