package cc.ai.zz.feature.ocr.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import androidx.core.graphics.createBitmap

class ScreenCaptureProvider(context: Context) {
    companion object {
        private const val TAG = "ScreenCaptureProvider"
        private const val CAPTURE_TIMEOUT_MS = 4000L
    }

    class ProjectionReauthorizationRequiredException :
        IllegalStateException("projection reauthorization required")

    private val appContext = context.applicationContext
    private val projectionManager =
        appContext.getSystemService(MediaProjectionManager::class.java)
    private val captureThread = HandlerThread("ocr-screen-capture").apply { start() }
    private val captureHandler = Handler(captureThread.looper)
    private val displayMetrics
        get() = appContext.resources.displayMetrics
    private var projectionResultCode: Int? = null
    private var projectionData: Intent? = null
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var captureWidth = 0
    private var captureHeight = 0
    private var captureDensityDpi = 0
    private var requiresReauthorization = false
    private val projectionCallback =
        object : MediaProjection.Callback() {
            override fun onStop() {
                Log.w(TAG, "media projection stopped by system")
                releaseCaptureSession()
                projectionResultCode = null
                projectionData = null
                requiresReauthorization = true
                mediaProjection = null
            }
        }

    fun updateProjectionGrant(resultCode: Int, data: Intent) {
        Log.d(TAG, "update projection grant resultCode=$resultCode")
        requiresReauthorization = false
        projectionResultCode = resultCode
        projectionData = Intent(data)
        releaseCaptureSession()
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        mediaProjection = null
    }

    fun prepareProjection(resultCode: Int, data: Intent) {
        Log.d(TAG, "prepare projection")
        updateProjectionGrant(resultCode, data)
        requireMediaProjection()
        ensureCaptureSession()
    }

    suspend fun capture(): Bitmap = suspendCancellableCoroutine { continuation ->
        val reader =
            try {
                ensureCaptureSession()
            } catch (error: Throwable) {
                continuation.resumeWithException(error)
                return@suspendCancellableCoroutine
            }
        var completed = false
        val timeoutRunnable = Runnable {
            if (completed) return@Runnable
            completed = true
            reader.setOnImageAvailableListener(null, null)
            Log.w(TAG, "capture timeout after ${CAPTURE_TIMEOUT_MS}ms")
            continuation.resumeWithException(IllegalStateException("screenshot timeout"))
        }
        val latestImage = reader.acquireLatestImage()
        if (latestImage != null) {
            try {
                completed = true
                continuation.resume(latestImage.toBitmap())
            } catch (error: Throwable) {
                continuation.resumeWithException(error)
            } finally {
                latestImage.close()
            }
            return@suspendCancellableCoroutine
        }
        reader.setOnImageAvailableListener(
            { reader ->
                if (completed) return@setOnImageAvailableListener
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val bitmap = image.toBitmap()
                    completed = true
                    captureHandler.removeCallbacks(timeoutRunnable)
                    reader.setOnImageAvailableListener(null, null)
                    continuation.resume(bitmap)
                } catch (error: Throwable) {
                    completed = true
                    captureHandler.removeCallbacks(timeoutRunnable)
                    reader.setOnImageAvailableListener(null, null)
                    continuation.resumeWithException(error)
                } finally {
                    image.close()
                }
            },
            captureHandler
        )
        captureHandler.postDelayed(timeoutRunnable, CAPTURE_TIMEOUT_MS)
        continuation.invokeOnCancellation {
            if (completed) return@invokeOnCancellation
            completed = true
            captureHandler.removeCallbacks(timeoutRunnable)
            reader.setOnImageAvailableListener(null, null)
        }
    }

    fun release() {
        Log.d(TAG, "release screen capture provider")
        releaseCaptureSession()
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        mediaProjection = null
        captureThread.quitSafely()
    }

    private fun requireMediaProjection(): MediaProjection {
        val existing = mediaProjection
        if (existing != null) return existing
        if (requiresReauthorization) {
            Log.w(TAG, "media projection requires reauthorization")
            throw ProjectionReauthorizationRequiredException()
        }
        val resultCode = projectionResultCode ?: error("projection grant missing")
        val data = projectionData ?: error("projection data missing")
        Log.d(TAG, "create media projection")
        val projection = requireNotNull(projectionManager.getMediaProjection(resultCode, Intent(data))) {
            "unable to create media projection"
        }
        return projection.also {
            projection.registerCallback(projectionCallback, captureHandler)
            mediaProjection = projection
        }
    }

    private fun ensureCaptureSession(): ImageReader {
        val projection = requireMediaProjection()
        val width = displayMetrics.widthPixels.coerceAtLeast(1)
        val height = displayMetrics.heightPixels.coerceAtLeast(1)
        val densityDpi = displayMetrics.densityDpi
        val currentReader = imageReader
        if (
            currentReader != null &&
            virtualDisplay != null &&
            captureWidth == width &&
            captureHeight == height &&
            captureDensityDpi == densityDpi
        ) {
            return currentReader
        }

        Log.d(TAG, "prepare capture session width=$width height=$height density=$densityDpi")
        val newReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val existingDisplay = virtualDisplay
        if (existingDisplay == null) {
            virtualDisplay = projection.createVirtualDisplay(
                "ocr-screen-capture",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                newReader.surface,
                null,
                captureHandler
            )
        } else {
            existingDisplay.resize(width, height, densityDpi)
            existingDisplay.surface = newReader.surface
            imageReader?.close()
        }
        imageReader = newReader
        captureWidth = width
        captureHeight = height
        captureDensityDpi = densityDpi
        return newReader
    }

    private fun releaseCaptureSession() {
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null
        virtualDisplay?.release()
        virtualDisplay = null
        captureWidth = 0
        captureHeight = 0
        captureDensityDpi = 0
    }

    private fun Image.toBitmap(): Bitmap {
        val plane = planes.first()
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val tempBitmap = createBitmap(width + rowPadding / pixelStride, height)
        tempBitmap.copyPixelsFromBuffer(buffer)
        val bitmap = Bitmap.createBitmap(tempBitmap, 0, 0, width, height)
        if (bitmap != tempBitmap) {
            tempBitmap.recycle()
        }
        return bitmap
    }
}
