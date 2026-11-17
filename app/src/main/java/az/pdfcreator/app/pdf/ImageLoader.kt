package az.pdfcreator.app.pdf

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/**
 * Memory-safe image decoding: reads bounds first, decodes with a power-of-two sample size,
 * and applies EXIF orientation plus the user's extra rotation.
 */
internal class ImageLoader(private val resolver: ContentResolver) {

    data class Info(
        val rawWidth: Int,
        val rawHeight: Int,
        /** Total clockwise rotation (EXIF + user), multiple of 90. */
        val rotation: Int,
        val flipHorizontal: Boolean,
    ) {
        val swaps: Boolean get() = rotation % 180 != 0
        val width: Int get() = if (swaps) rawHeight else rawWidth
        val height: Int get() = if (swaps) rawWidth else rawHeight
    }

    fun info(uri: Uri, userRotation: Int): Info? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } }
            .getOrElse { return null }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null

        var exifRotation = 0
        var flip = false
        runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                exifRotation = exif.rotationDegrees
                flip = exif.isFlipped
            }
        }
        val total = ((exifRotation + userRotation) % 360 + 360) % 360
        return Info(opts.outWidth, opts.outHeight, total, flip)
    }

    /**
     * Returns the oriented image, scaled down so that it is no larger than needed for
     * [targetWidth] x [targetHeight] (oriented pixels). Never upscales. Null if unreadable.
     */
    fun decode(uri: Uri, info: Info, targetWidth: Int, targetHeight: Int): Bitmap? {
        // Sample size is computed in raw (unrotated) space.
        val reqW = if (info.swaps) targetHeight else targetWidth
        val reqH = if (info.swaps) targetWidth else targetHeight
        var sample = 1
        while (info.rawWidth / (sample * 2) >= reqW && info.rawHeight / (sample * 2) >= reqH) sample *= 2
        // Hard safety cap: never decode more than ~40 MP even for absurd targets.
        while ((info.rawWidth.toLong() / sample) * (info.rawHeight.toLong() / sample) > 40_000_000L) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = try {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (_: OutOfMemoryError) {
            // Retry once with half resolution.
            opts.inSampleSize = sample * 2
            runCatching { resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } }.getOrNull()
        } catch (_: Exception) {
            null
        } ?: return null

        if (info.rotation == 0 && !info.flipHorizontal) return raw
        val m = Matrix()
        if (info.flipHorizontal) m.postScale(-1f, 1f)
        if (info.rotation != 0) m.postRotate(info.rotation.toFloat())
        return try {
            val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            if (rotated !== raw) raw.recycle()
            rotated
        } catch (_: OutOfMemoryError) {
            raw.recycle()
            null
        }
    }
}
