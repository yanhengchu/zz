package cc.ai.zz.feature.overlay.view

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import cc.ai.zz.feature.overlay.store.OverlayPositionKey
import java.util.Locale

class CoordinateLocatorFloatingView
@JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    BaseFloatingView(context, attrs, defStyleAttr) {
    private val statusBarHeightPx by lazy {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId == 0) 0 else resources.getDimensionPixelSize(resourceId)
    }

    override val positionKey = OverlayPositionKey(
        xKey = "floating_coordinate_locator_x",
        yKey = "floating_coordinate_locator_y"
    )

    private fun buildRatioTextView() = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(0xFFFFFFFF.toInt())
        textSize = 12f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        includeFontPadding = false
        setLineSpacing(0f, 0.9f)
    }

    private val xRatioTextView = buildRatioTextView().apply {
        text = "0.00"
    }

    private val yRatioTextView = buildRatioTextView().apply {
        text = "0.00"
    }

    init {
        layoutParams = LayoutParams(sizeW, sizeH)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 28f
            setColor(0xEE14324D.toInt())
            setStroke(3, 0x66D7F2FF)
        }
        addView(
            LinearLayout(context).apply {
                gravity = Gravity.CENTER
                orientation = LinearLayout.VERTICAL
                setPadding(8, 10, 8, 10)
                addView(xRatioTextView)
                addView(yRatioTextView)
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    override fun onPositionChanged(screenX: Int, screenY: Int, isFinal: Boolean) {
        val displayMetrics = resources.displayMetrics
        val centerX = (screenX + sizeW / 2f) / displayMetrics.widthPixels.toFloat()
        val centerY = (screenY + statusBarHeightPx + sizeH / 2f) / displayMetrics.heightPixels.toFloat()
        xRatioTextView.text = String.format(Locale.US, "%.2f", centerX.coerceIn(0f, 1f))
        yRatioTextView.text = String.format(Locale.US, "%.2f", centerY.coerceIn(0f, 1f))
    }
}
