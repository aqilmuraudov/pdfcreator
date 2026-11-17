package az.pdfcreator.app.pdf

import java.io.InputStream
import java.util.Locale

/**
 * Small Hunspell-style Azerbaijani word checker: stems + one level of suffix rules (az.aff).
 * Checks a word without expanding all ~680k forms, so it stays tiny in memory and on disk.
 */
class AzSpell(dic: InputStream, aff: InputStream, extra: InputStream?) {

    private class Rule(val flag: String, val strip: String, val add: String, val cond: Regex?)

    /** stem -> its two-letter flags concatenated */
    private val stems = HashMap<String, String>(60_000)
    /** suffix ("add") -> rules producing it, for quick lookup from the end of a word */
    private val byAdd = HashMap<String, MutableList<Rule>>()
    private val maxAdd: Int

    init {
        aff.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { raw ->
                val p = raw.substringBefore('#').trim().split(Regex("\\s+"))
                if (p.size >= 5 && p[0] == "SFX") {
                    val strip = if (p[2] == "0") "" else p[2]
                    val add = if (p[3] == "0") "" else p[3].substringBefore('/')
                    val cond = if (p[4] == ".") null else Regex("(${p[4]})$")
                    byAdd.getOrPut(add) { ArrayList() }.add(Rule(p[1], strip, add, cond))
                }
            }
        }
        maxAdd = byAdd.keys.maxOfOrNull { it.length } ?: 0
        dic.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.drop(1).forEach { line ->
                if (line.isBlank()) return@forEach
                val word = lowerAz(line.substringBefore('/').trim())
                val flags = line.substringAfter('/', "").trim()
                stems[word] = (stems[word] ?: "") + flags
            }
        }
        extra?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
            lines.flatMap { it.split(Regex("\\s+")).asSequence() }.filter { it.isNotBlank() }
                .forEach { stems.putIfAbsent(lowerAz(it), "") }
        }
    }

    /** True if [word] (lower-case) is a known Azerbaijani word form. */
    fun isWord(word: String): Boolean {
        if (stems.containsKey(word)) return true
        for (len in 1..minOf(maxAdd, word.length - 1)) {
            val add = word.substring(word.length - len)
            val rules = byAdd[add] ?: continue
            val base = word.substring(0, word.length - len)
            for (r in rules) {
                val stem = base + r.strip
                val flags = stems[stem] ?: continue
                if (!hasFlag(flags, r.flag)) continue
                if (r.cond == null || r.cond.containsMatchIn(stem)) return true
            }
        }
        return false
    }

    private fun hasFlag(flags: String, flag: String): Boolean {
        var i = 0
        while (i + 1 < flags.length) {
            if (flags[i] == flag[0] && flags[i + 1] == flag[1]) return true
            i += 2
        }
        return false
    }

    companion object {
        private val AZ = Locale.forLanguageTag("az")
        fun lowerAz(s: String) = s.replace('I', 'ı').replace('İ', 'i').lowercase(AZ)
    }
}

/**
 * Fixes typical OCR mistakes in Azerbaijani text (ML Kit's Latin model is not trained for it):
 * "ɔ"→"ə", "Mügavilə"→"Müqavilə", "Isçi"→"İşçi", "va"→"və"… Only changes a word when the
 * original is NOT a known word and the replacement IS one; corrections go only in the
 * direction OCR typically errs, so correct-but-unknown words are left alone.
 */
class AzOcrFixer(private val spell: AzSpell) {

    fun fix(text: String): String {
        val t = normalize(text)
        if (!looksAzerbaijani(t)) return t
        val words = WORD.replace(t) { m -> fixWord(m.value) }
        return STANDALONE_VA.replace(words) { m -> if (m.value[0] == 'V') "Və" else "və" }
    }

    private fun fixWord(o: String): String {
        if (o.length < 2) return o
        val lw = AzSpell.lowerAz(o)
        // "açıqlanmasI": a capital I inside a lower-case word is really "ı".
        val strayI = o.length > 2 && o.substring(1).contains('I') && o.any { it.isLowerCase() }
        if (spell.isWord(lw)) return if (strayI) restoreCase(o, lw) else o
        val c = candidate(lw) ?: return o
        return restoreCase(o, c)
    }

    /** Best known word reachable with ≤ 2 OCR-typical substitutions (fewest first). */
    private fun candidate(w: String): String? {
        val starts = ArrayList<Pair<String, Int>>(2)
        starts.add(w to 0)
        if (w[0] == 'l' || w[0] == 'ı') starts.add(("i" + w.substring(1)) to 1)
        var best: String? = null
        var bestCost = Int.MAX_VALUE
        for ((base, c0) in starts) {
            if (c0 < bestCost && spell.isWord(base)) { best = base; bestCost = c0 }
            for (i in base.indices) {
                val reps = CONF[base[i]] ?: continue
                for (r in reps) {
                    val v = base.substring(0, i) + r + base.substring(i + 1)
                    if (c0 + 1 < bestCost && spell.isWord(v)) { best = v; bestCost = c0 + 1 }
                    if (c0 + 2 >= bestCost) continue
                    for (j in i + 1 until v.length) {
                        val reps2 = CONF[v[j]] ?: continue
                        for (r2 in reps2) {
                            val v2 = v.substring(0, j) + r2 + v.substring(j + 1)
                            if (c0 + 2 < bestCost && spell.isWord(v2)) { best = v2; bestCost = c0 + 2 }
                        }
                    }
                }
            }
            if (bestCost <= 1) break
        }
        return best
    }

    private fun restoreCase(orig: String, fixed: String): String {
        val letters = orig.filter { it.isLetter() }
        if (letters.length > 1 && letters.all { it.isUpperCase() }) return fixed.uppercase(AZ)
        // Capitalised, or a lower-case "l" that was really a capital "İ" ("lşçi" -> "İşçi").
        val capital = orig[0].isUpperCase() || (orig[0] == 'l' && fixed[0] == 'i')
        if (!capital) return fixed
        val upper = when (fixed[0]) { 'i' -> 'İ'; 'ı' -> 'I'; else -> fixed[0].uppercaseChar() }
        return upper + fixed.substring(1)
    }

    companion object {
        private val AZ = Locale.forLanguageTag("az")
        private val WORD = Regex("[A-Za-zÇçƏəĞğIıİiÖöŞşÜü]+")
        private val STANDALONE_VA = Regex("\\b[Vv]a\\b")
        private const val AZ_LETTERS = "əƏğĞşŞıİçÇöÖüÜ"

        /** OCR → truth substitutions only (never the other way). */
        private val CONF: Map<Char, String> = mapOf(
            'a' to "ə", 'e' to "ə", 'o' to "əö", 's' to "şə", 'g' to "qğ", 'u' to "ü",
            'c' to "ç", 'i' to "ı", 'l' to "ı",
        )

        fun normalize(t: String): String {
            var s = t
            for ((a, b) in listOf("ɔ" to "ə", "Ɔ" to "Ə", "Í" to "İ", "Ì" to "İ", "ł" to "l", "Ț" to "T", "ț" to "t", "ȘS" to "Ş")) s = s.replace(a, b)
            return s
        }

        /** Azerbaijani if a noticeable share of letters are Azerbaijani-specific. */
        fun looksAzerbaijani(t: String): Boolean {
            var letters = 0
            var az = 0
            for (ch in t) {
                if (ch.isLetter()) {
                    letters++
                    if (AZ_LETTERS.indexOf(ch) >= 0) az++
                }
            }
            return letters > 0 && az * 100 >= letters * 3
        }
    }
}

/**
 * Puts OCR lines into natural reading order: top-to-bottom rows, left-to-right inside a row
 * ("1.1." joins its paragraph, centred titles come first), blank line between paragraphs.
 */
object ReadingOrder {
    data class Box(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val centerY get() = (top + bottom) / 2f
        val height get() = bottom - top
    }

    fun join(lines: List<Box>): String {
        if (lines.isEmpty()) return ""
        val heights = lines.map { it.height }.sorted()
        val med = heights[heights.size / 2].coerceAtLeast(1)
        val rows = ArrayList<MutableList<Box>>()
        for (l in lines.sortedBy { it.centerY }) {
            val row = rows.lastOrNull()
            if (row != null && kotlin.math.abs(l.centerY - row.map { it.centerY }.average()) < med * 0.6) row.add(l)
            else rows.add(mutableListOf(l))
        }
        val sb = StringBuilder()
        var prevBottom = -1
        for (row in rows) {
            val top = row.minOf { it.top }
            if (sb.isNotEmpty()) sb.append(if (top - prevBottom > med * 0.9) "\n\n" else "\n")
            sb.append(row.sortedBy { it.left }.joinToString(" ") { it.text.trim() })
            prevBottom = row.maxOf { it.bottom }
        }
        return sb.toString()
    }
}
