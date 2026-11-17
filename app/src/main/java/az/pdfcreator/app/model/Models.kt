package az.pdfcreator.app.model

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** One picked image. [rotation] is the extra clockwise rotation chosen by the user (0/90/180/270). */
data class PageItem(
    val id: Long,
    val uri: Uri,
    val rotation: Int = 0,
)

enum class PageSizeOption(val widthPt: Float, val heightPt: Float) {
    A4(595.28f, 841.89f),
    LETTER(612f, 792f),
    LEGAL(612f, 1008f),
    A5(419.53f, 595.28f),
    A3(841.89f, 1190.55f),
    FIT_IMAGE(0f, 0f),
}

enum class OrientationOption { AUTO, PORTRAIT, LANDSCAPE }

enum class MarginOption(val pt: Float) { NONE(0f), SMALL(18f), MEDIUM(36f), LARGE(54f) }

enum class ScaleMode { FIT, FILL }

/** [dpi]: target resolution of images on the page, [jpeg]: JPEG quality, [estKb]: rough size of a full-page image. */
enum class QualityOption(val dpi: Int, val jpeg: Int, val estKb: Int) {
    LOW(100, 60, 90),
    MEDIUM(150, 80, 260),
    HIGH(220, 88, 600),
    MAX(300, 95, 1300),
}

enum class ColorMode { ORIGINAL, GRAYSCALE, DOCUMENT }

enum class Layout { NORMAL, ID_CARD }

enum class SignaturePages { LAST, ALL }

enum class SignaturePos { RIGHT, CENTER, LEFT }

/** Ink colours for the signature (blue looks like a pen). */
enum class SignatureInk(val r: Int, val g: Int, val b: Int) { BLUE(26, 58, 143), BLACK(20, 20, 30) }

data class PdfOptions(
    val fileName: String = "",
    val pageSize: PageSizeOption = PageSizeOption.A4,
    val orientation: OrientationOption = OrientationOption.AUTO,
    val margin: MarginOption = MarginOption.SMALL,
    val scaleMode: ScaleMode = ScaleMode.FIT,
    val imagesPerPage: Int = 1,
    val quality: QualityOption = QualityOption.MEDIUM,
    val colorMode: ColorMode = ColorMode.ORIGINAL,
    val pageNumbers: Boolean = false,
    val watermark: String = "",
    val password: String = "",
    /** 0 = no limit. Otherwise quality is lowered automatically until the file fits. */
    val maxSizeKb: Int = 0,
    val signature: Boolean = false,
    val signaturePages: SignaturePages = SignaturePages.LAST,
    val signaturePos: SignaturePos = SignaturePos.RIGHT,
    val signatureInk: SignatureInk = SignatureInk.BLUE,
    val layout: Layout = Layout.NORMAL,
    /** ID card mode: 1.0 = real size (85.6 x 54 mm), larger values enlarge. */
    val idCardScale: Float = 1f,
    /** Run OCR and add an invisible text layer so the PDF can be searched and copied. */
    val searchable: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("fileName", fileName)
        .put("pageSize", pageSize.name)
        .put("orientation", orientation.name)
        .put("margin", margin.name)
        .put("scaleMode", scaleMode.name)
        .put("imagesPerPage", imagesPerPage)
        .put("quality", quality.name)
        .put("colorMode", colorMode.name)
        .put("pageNumbers", pageNumbers)
        .put("watermark", watermark)
        .put("password", password)
        .put("maxSizeKb", maxSizeKb)
        .put("signature", signature)
        .put("signaturePages", signaturePages.name)
        .put("signaturePos", signaturePos.name)
        .put("signatureInk", signatureInk.name)
        .put("layout", layout.name)
        .put("idCardScale", idCardScale.toDouble())
        .put("searchable", searchable)

    /** Rough output size in bytes, shown before creating the PDF. */
    fun estimateBytes(imageCount: Int): Long {
        if (imageCount == 0) return 0
        val perImageKb = when (imagesPerPage) {
            1 -> quality.estKb.toDouble()
            2 -> quality.estKb * 0.55
            else -> quality.estKb * 0.3
        }
        val colorFactor = if (colorMode == ColorMode.ORIGINAL) 1.0 else 0.7
        return (imageCount * perImageKb * colorFactor * 1024).toLong()
    }

    companion object {
        fun fromJson(o: JSONObject) = PdfOptions(
            fileName = o.optString("fileName"),
            pageSize = enumOr(o.optString("pageSize"), PageSizeOption.A4),
            orientation = enumOr(o.optString("orientation"), OrientationOption.AUTO),
            margin = enumOr(o.optString("margin"), MarginOption.SMALL),
            scaleMode = enumOr(o.optString("scaleMode"), ScaleMode.FIT),
            imagesPerPage = o.optInt("imagesPerPage", 1).let { if (it in setOf(1, 2, 4)) it else 1 },
            quality = enumOr(o.optString("quality"), QualityOption.MEDIUM),
            colorMode = enumOr(o.optString("colorMode"), ColorMode.ORIGINAL),
            pageNumbers = o.optBoolean("pageNumbers", false),
            watermark = o.optString("watermark"),
            password = o.optString("password"),
            maxSizeKb = o.optInt("maxSizeKb", 0),
            signature = o.optBoolean("signature", false),
            signaturePages = enumOr(o.optString("signaturePages"), SignaturePages.LAST),
            signaturePos = enumOr(o.optString("signaturePos"), SignaturePos.RIGHT),
            signatureInk = enumOr(o.optString("signatureInk"), SignatureInk.BLUE),
            layout = enumOr(o.optString("layout"), Layout.NORMAL),
            idCardScale = o.optDouble("idCardScale", 1.0).toFloat(),
            searchable = o.optBoolean("searchable", false),
        )

        inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
            enumValues<T>().firstOrNull { it.name == name } ?: default
    }
}

/** Everything the background worker needs; stored as a file because WorkManager input is limited to 10 KB. */
data class PdfJob(val items: List<PageItem>, val options: PdfOptions) {
    fun toJson(): String = JSONObject()
        .put("options", options.toJson())
        .put("items", JSONArray().apply {
            items.forEach { put(JSONObject().put("uri", it.uri.toString()).put("rotation", it.rotation)) }
        })
        .toString()

    companion object {
        fun fromJson(s: String): PdfJob {
            val o = JSONObject(s)
            val arr = o.getJSONArray("items")
            val items = (0 until arr.length()).map { i ->
                val it = arr.getJSONObject(i)
                PageItem(i.toLong(), Uri.parse(it.getString("uri")), it.optInt("rotation", 0))
            }
            return PdfJob(items, PdfOptions.fromJson(o.getJSONObject("options")))
        }
    }
}

data class HistoryEntry(
    val name: String,
    val uri: String,
    val filePath: String?,
    val sizeBytes: Long,
    val pages: Int,
    val createdAt: Long,
    val protected: Boolean,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name).put("uri", uri).put("filePath", filePath ?: "")
        .put("size", sizeBytes).put("pages", pages).put("createdAt", createdAt)
        .put("protected", protected)

    companion object {
        fun fromJson(o: JSONObject) = HistoryEntry(
            name = o.optString("name"),
            uri = o.optString("uri"),
            filePath = o.optString("filePath").ifEmpty { null },
            sizeBytes = o.optLong("size"),
            pages = o.optInt("pages"),
            createdAt = o.optLong("createdAt"),
            protected = o.optBoolean("protected"),
        )
    }
}
