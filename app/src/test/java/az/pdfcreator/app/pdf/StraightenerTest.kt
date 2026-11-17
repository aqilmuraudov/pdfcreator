package az.pdfcreator.app.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class StraightenerTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** A white page with dark "text lines", rotated by [tilt] degrees. */
    private fun page(tilt: Float, w: Int = 1240, h: Int = 1754): Uri {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        c.rotate(tilt, w / 2f, h / 2f)
        val p = Paint().apply { color = Color.rgb(30, 30, 30) }
        var y = 160f
        var i = 0
        while (y < h - 160) {
            // words: short dark blocks separated by gaps
            var x = 120f
            while (x < w - 160) {
                val len = 30f + (i * 37 % 90)
                c.drawRect(x, y, x + len, y + 16, p)
                x += len + 18; i++
            }
            y += 46f
        }
        val f = File(ctx.cacheDir, "page_${tilt}_${w}x$h.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return Uri.fromFile(f)
    }

    private fun residual(uri: Uri): Float {
        val bmp = BitmapFactory.decodeFile(uri.path)
        val scale = 1000f / maxOf(bmp.width, bmp.height)
        val s = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
        val px = IntArray(s.width * s.height).also { s.getPixels(it, 0, s.width, 0, 0, s.width, s.height) }
        val luma = IntArray(px.size) { (Color.red(px[it]) * 299 + Color.green(px[it]) * 587 + Color.blue(px[it]) * 114) / 1000 }
        return Deskew.estimate(luma, s.width, s.height)?.correction ?: 0f
    }

    @Test fun straightensTiltedPage() {
        val out = Straightener(ctx, File(ctx.filesDir, "st")).straighten(page(3f))
        assertNotNull("tilted page must be corrected", out)
        val r = residual(out!!)
        assertTrue("residual tilt $r should be < 0.4°", abs(r) < 0.4f)
    }

    @Test fun leavesStraightPageAlone() {
        assertNull(Straightener(ctx, File(ctx.filesDir, "st")).straighten(page(0f)))
    }

    @Test fun restoresA4ShapeOfSquashedScan() {
        // Like the user's scan: an A4 sheet that came out 0.89 wide instead of 0.707.
        val out = Straightener(ctx, File(ctx.filesDir, "st")).straighten(page(0f, 1000, 1124))
        assertNotNull(out)
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(out!!.path, o)
        val ratio = o.outWidth.toFloat() / o.outHeight
        assertTrue("ratio $ratio should be A4 (0.707)", abs(ratio - 0.7071f) < 0.01f)
        assertTrue("never shrinks", o.outWidth == 1000)
    }

    @Test fun leavesIdCardShapeAlone() {
        // 0.63 (ID card) is narrower than A4: must not be stretched.
        assertNull(Straightener(ctx, File(ctx.filesDir, "st")).straighten(page(0f, 1000, 1587)))
    }

    @Test fun ignoresPhotos() {
        val bmp = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(90, 140, 200)) }
        val f = File(ctx.cacheDir, "photo.jpg").also { o -> o.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
        assertNull(Straightener(ctx, File(ctx.filesDir, "st")).straighten(Uri.fromFile(f)))
    }
}
