package az.pdfcreator.app.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

class AzOcrFixerTest {
    private val assets = File("src/main/assets/spell")
    private val fixer by lazy {
        AzOcrFixer(
            AzSpell(
                GZIPInputStream(File(assets, "az.dic.gz").inputStream()),
                File(assets, "az.aff").inputStream(),
                File(assets, "extra.txt").inputStream(),
            ),
        )
    }

    @Test fun fixesTypicalMlKitErrors() {
        val ocr = "Bu Mɔxfilik mügaviləsi aşağıdakı şəxslər arasında baglanmışdır. Isçi Işəgötürən vɔ cadvəllər, gadər, haggı, açıqlanmasI"
        val fixed = fixer.fix(ocr)
        assertEquals("Bu Məxfilik müqaviləsi aşağıdakı şəxslər arasında bağlanmışdır. İşçi İşəgötürən və cədvəllər, qədər, haqqı, açıqlanması", fixed)
    }

    @Test fun keepsOtherLanguagesUntouched() {
        val en = "Hello world, this is a gas station and a coffee shop."
        assertEquals(en, fixer.fix(en))
        val ru = "Привет мир, это русский текст."
        assertEquals(ru, fixer.fix(ru))
    }

    @Test fun readingOrder() {
        val lines = listOf(
            ReadingOrder.Box("Müqavilə ilə İşçi", 120, 300, 900, 330),
            ReadingOrder.Box("MƏXFİLİK MÜQAVİLƏSİ", 300, 100, 700, 130),
            ReadingOrder.Box("1.1.", 40, 302, 100, 328),
            ReadingOrder.Box("fəaliyyətdən irəli", 40, 340, 900, 370),
        )
        val text = ReadingOrder.join(lines)
        assertTrue(text, text.startsWith("MƏXFİLİK MÜQAVİLƏSİ\n\n1.1. Müqavilə ilə İşçi\nfəaliyyətdən irəli"))
    }
}
