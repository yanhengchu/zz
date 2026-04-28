package cc.ai.zz.feature.ocr.coordinator

import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import cc.ai.zz.app.MyApp
import cc.ai.zz.feature.automation.executor.AccessibilityGestureExecutor
import cc.ai.zz.feature.automation.executor.GestureAccessibilityService
import cc.ai.zz.feature.ocr.capture.ScreenCaptureProvider
import cc.ai.zz.feature.ocr.recognize.LocalOcrProvider
import cc.ai.zz.feature.ocr.rule.OcrRuleEngine
import cc.ai.zz.feature.overlay.manager.OcrStatusFloatingWindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OcrCoordinator(
    private val onStopped: (() -> Unit)? = null,
    private val onReauthorizationRequired: (() -> Unit)? = null,
    private val onShowMessage: ((String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "OcrCoordinator"
        private const val OCR_INTERVAL_MS = 2000L
        private const val MAX_OCR_LINE_LENGTH = 24
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val screenCaptureProvider by lazy { ScreenCaptureProvider(MyApp.context) }
    private val localOcrProvider by lazy { LocalOcrProvider() }
    private val ocrRuleEngine by lazy {
        OcrRuleEngine(
            context = MyApp.context,
            executorProvider = {
                GestureAccessibilityService.instance?.let(::AccessibilityGestureExecutor)
            },
            onShowMessage = { message -> onShowMessage?.invoke(message) }
        )
    }
    private var pollingRunnable: Runnable? = null
    private var currentRoundJob: Job? = null
    private var isRoundRunning = false
    private var sessionId = 0L
    val isActive: Boolean
        get() = pollingRunnable != null

    fun start(resultCode: Int, projectionData: Intent) {
        sessionId += 1
        Log.d(TAG, "start OCR polling session=$sessionId resultCode=$resultCode")
        ocrRuleEngine.resetRuntimeState()
        screenCaptureProvider.prepareProjection(resultCode, projectionData)
        ocrRuleEngine.reloadRules()
        OcrStatusFloatingWindowManager.tryShow(status = "waiting")
        val nextRunnable = Runnable { triggerRound() }
        pollingRunnable = nextRunnable
        nextRunnable.run()
    }

    fun stop() {
        sessionId += 1
        Log.d(TAG, "stop OCR polling session=$sessionId isRoundRunning=$isRoundRunning")
        pollingRunnable = null
        ocrRuleEngine.resetRuntimeState()
        currentRoundJob?.cancel()
        currentRoundJob = null
        isRoundRunning = false
        OcrStatusFloatingWindowManager.tryHide()
        onStopped?.invoke()
    }

    fun release() {
        stop()
        scope.coroutineContext.cancel()
        localOcrProvider.release()
        screenCaptureProvider.release()
    }

    private fun triggerRound() {
        val currentSessionId = sessionId
        if (isRoundRunning) {
            Log.d(TAG, "skip OCR round because previous round is still running session=$currentSessionId")
            scheduleNext(currentSessionId)
            return
        }
        isRoundRunning = true
        currentRoundJob = scope.launch {
            val packageName = resolveCurrentPackageName()
            var roundStatus = "waiting"
            var bitmap: android.graphics.Bitmap? = null
            var ocrBitmap: Bitmap? = null
            try {
                Log.d(TAG, "ocr round started session=$currentSessionId pkg=$packageName")
                ensureSessionActive(currentSessionId)
                bitmap = screenCaptureProvider.capture()
                ensureSessionActive(currentSessionId)
                ocrBitmap = cropStatusBar(bitmap)
                val text = ocrRuleEngine.normalizeRecognizedText(localOcrProvider.recognize(ocrBitmap))
                val logText = formatForLog(text)
                ensureSessionActive(currentSessionId)
                roundStatus = if (text.isBlank()) {
                    "none"
                } else {
                    ensureSessionActive(currentSessionId)
                    ocrRuleEngine.handleRecognizedText(packageName, text).also { result ->
                        if (result.shouldLogText) {
                            Log.d(TAG, "ocr result session=$currentSessionId pkg=$packageName text=$logText")
                        }
                    }.status
                }
                if (roundStatus == "none" && text.isNotBlank()) {
                    Log.d(TAG, "ocr result session=$currentSessionId pkg=$packageName text=$logText")
                }
                Log.d(TAG, "ocr round finished session=$currentSessionId textLength=${text.length}")
            } catch (error: Throwable) {
                Log.e(TAG, "ocr round failed", error)
                if (!isSessionActive(currentSessionId)) {
                    return@launch
                }
                if (error is ScreenCaptureProvider.ProjectionReauthorizationRequiredException) {
                    Log.w(TAG, "projection reauthorization required session=$currentSessionId")
                    onReauthorizationRequired?.invoke()
                    stop()
                    return@launch
                }
                roundStatus = mapError(error)
                Log.e(TAG, "ocr round mappedError=$roundStatus pkg=$packageName")
            } finally {
                if (ocrBitmap != null && ocrBitmap !== bitmap) {
                    ocrBitmap.recycle()
                }
                bitmap?.recycle()
                if (isSessionActive(currentSessionId)) {
                    OcrStatusFloatingWindowManager.updateStatus(roundStatus)
                    isRoundRunning = false
                    currentRoundJob = null
                    scheduleNext(currentSessionId, OCR_INTERVAL_MS)
                }
            }
        }
    }

    private fun scheduleNext(expectedSessionId: Long, delayMs: Long = OCR_INTERVAL_MS) {
        val runnable = pollingRunnable ?: return
        Log.d(TAG, "schedule next OCR round session=$expectedSessionId delayMs=$delayMs")
        scope.launch {
            delay(delayMs)
            if (expectedSessionId != sessionId) return@launch
            runnable.run()
        }
    }

    private fun ensureSessionActive(expectedSessionId: Long) {
        check(isSessionActive(expectedSessionId)) { "ocr session expired" }
    }

    private fun isSessionActive(expectedSessionId: Long): Boolean {
        return expectedSessionId == sessionId && pollingRunnable != null
    }

    private fun resolveCurrentPackageName(): String {
        return GestureAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString()
            ?: "unknown"
    }

    private fun formatForLog(text: String): String {
        return text
            .lineSequence()
            .map(::truncateLineForLog)
            .joinToString("\n")
    }

    private fun truncateLineForLog(line: String): String {
        if (line.length <= MAX_OCR_LINE_LENGTH) return line
        return line.take(MAX_OCR_LINE_LENGTH) + "..."
    }

    private fun cropStatusBar(bitmap: Bitmap): Bitmap {
        val statusBarHeight = resolveStatusBarHeight()
        if (statusBarHeight <= 0 || statusBarHeight >= bitmap.height) return bitmap
        return Bitmap.createBitmap(bitmap, 0, statusBarHeight, bitmap.width, bitmap.height - statusBarHeight)
    }

    private fun resolveStatusBarHeight(): Int {
        val resources = MyApp.context.resources
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId == 0) return 0
        return resources.getDimensionPixelSize(resourceId)
    }

    private fun mapError(error: Throwable): String {
        val message = error.message?.take(40)?.replace('\n', ' ')?.trim().orEmpty()
        return when {
            error is ScreenCaptureProvider.ProjectionReauthorizationRequiredException -> "projection_reauth_required"
            message.contains("projection grant missing", ignoreCase = true) -> "projection_missing"
            message.contains("projection data missing", ignoreCase = true) -> "projection_data_missing"
            message.contains("unable to create media projection", ignoreCase = true) -> "projection_create_failed"
            message.contains("screenshot timeout", ignoreCase = true) -> "screenshot_timeout"
            else -> "failed:${error.javaClass.simpleName}"
        }
    }
}
