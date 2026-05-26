package cc.ai.zz.feature.overlay.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt

class OcrStatusFloatingView(context: Context) : FrameLayout(context) {
    companion object {
        private const val TOP_MARGIN_DP = 24
        private const val EXTRA_TOP_OFFSET_DP = 44
    }

    private val windowManager by lazy {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    private val textView =
        TextView(context).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor("#99000000".toColorInt())
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
        }
    private var isAttached = false
    private var statusText = "boot"

    init {
        addView(textView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        elevation = dp(6).toFloat()
        render()
    }

    fun show() {
        showOnMain()
    }

    fun hide() {
        hideOnMain()
    }

    fun updateStatus(status: String) {
        statusText = status
        render()
    }

    @SuppressLint("SetTextI18n")
    private fun render() {
        textView.text = "ocr: ${formatStatus(statusText)}"
    }

    private fun formatStatus(rawStatus: String): String {
        return when {
            rawStatus.startsWith("skip:") -> formatTaggedStatus(rawStatus, "skip")
            rawStatus.startsWith("fail:") -> formatTaggedStatus(rawStatus, "fail")
            else -> rawStatus
        }
    }

    private fun formatTaggedStatus(rawStatus: String, suffix: String): String {
        val ruleId = rawStatus.substringAfter(':', "").trim()
        if (ruleId.isEmpty()) return suffix
        return "$ruleId/$suffix"
    }

    private fun showOnMain() {
        if (isAttached) return
        val params =
            WindowManager.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = dp(12)
                y = dp(TOP_MARGIN_DP + EXTRA_TOP_OFFSET_DP)
            }
        windowManager.addView(this, params)
        isAttached = true
    }

    private fun hideOnMain() {
        if (!isAttached) return
        windowManager.removeView(this)
        isAttached = false
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
