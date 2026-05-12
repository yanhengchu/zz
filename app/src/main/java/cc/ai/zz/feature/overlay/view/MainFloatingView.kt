package cc.ai.zz.feature.overlay.view

import android.content.Context
import android.os.Handler
import android.util.AttributeSet
import cc.ai.zz.feature.overlay.store.OverlayPositionKey

class MainFloatingView
@JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    FloatingRingUIView(context, attrs, defStyleAttr) {
    private val handler = Handler(context.mainLooper)
    private var currentTimeS = -1L
    private var timeLimitS = 0L
    private val countdownRunnable = object : Runnable {
        override fun run() {
            currentTimeS--
            renderCountdown(currentTimeS)
            handler.postDelayed(this, 1000)
        }
    }

    override val positionKey = OverlayPositionKey(
        xKey = "floating_main_view_x",
        yKey = "floating_main_view_y"
    )

    init {
        renderCountdown(0)
    }

    fun updateTimeLimit(limitMs: Long) {
        updateTimeLimitOnMain(limitMs)
    }

    fun stopCountdown() {
        handler.removeCallbacks(countdownRunnable)
    }

    private fun renderCountdown(timeS: Long) {
        currentTimeS = timeS.coerceIn(0..timeLimitS)
        updateCountdownText((currentTimeS - 1).coerceAtLeast(0))
    }

    private fun updateTimeLimitOnMain(limitMs: Long) {
        timeLimitS = limitMs.div(1000).coerceAtLeast(0)
        handler.removeCallbacks(countdownRunnable)
        renderCountdown(timeLimitS)
        if (timeLimitS > 0) {
            handler.postDelayed(countdownRunnable, 1000)
        }
    }
}
