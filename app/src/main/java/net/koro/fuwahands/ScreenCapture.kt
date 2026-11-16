package net.koro.fuwahands

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.ByteArrayOutputStream

/**
 * MediaProjection screen capture. Requires the user's consent result
 * (from MediaProjectionManager.createScreenCaptureIntent) each time.
 * Keeps the latest frame in memory; /screenshot compresses it on demand.
 */
object ScreenCapture {
    @Volatile var active: Boolean = false
        private set
    @Volatile var width: Int = 0
        private set
    @Volatile var height: Int = 0
        private set

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null

    @Volatile private var lastFrame: Bitmap? = null

    fun start(ctx: Context, resultCode: Int, data: Intent) {
        stop()
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        width = metrics.widthPixels
        height = metrics.heightPixels
        val density = metrics.densityDpi

        val mpm = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val proj = mpm.getMediaProjection(resultCode, data)
        projection = proj

        val ht = HandlerThread("fuwa-capture")
        ht.start()
        thread = ht
        val handler = Handler(ht.looper)

        // Required on Android 14 before createVirtualDisplay.
        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stop() }
        }, handler)

        val r = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader = r
        r.setOnImageAvailableListener({ ir ->
            val image = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                val padded = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                padded.copyPixelsFromBuffer(buffer)
                val clean = if (rowPadding == 0) padded else Bitmap.createBitmap(padded, 0, 0, width, height)
                lastFrame = clean
            } catch (_: Exception) {
                // frame dropped; next one will arrive shortly
            } finally {
                image.close()
            }
        }, handler)

        display = proj.createVirtualDisplay(
            "fuwa-hands", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            r.surface, null, handler
        )
        active = true
    }

    fun stop() {
        active = false
        try { display?.release() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        display = null; reader = null; projection = null
        thread?.quitSafely(); thread = null
        lastFrame = null
    }

    /** Latest frame as JPEG bytes, or null if no frame yet. */
    fun jpeg(quality: Int = 85): ByteArray? {
        val bmp = lastFrame ?: return null
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }
}
