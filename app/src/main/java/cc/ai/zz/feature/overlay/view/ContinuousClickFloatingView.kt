package cc.ai.zz.feature.overlay.view

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.TextView
import cc.ai.zz.feature.overlay.store.OverlayPositionKey

class ContinuousClickFloatingView
@JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    BaseFloatingView(context, attrs, defStyleAttr) {
    override val positionKey = OverlayPositionKey(
        xKey = "floating_continuous_click_x",
        yKey = "floating_continuous_click_y"
    )

    var toggleListener: (() -> Unit)? = null

    private val stateTextView = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(0xFFFFFFFF.toInt())
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
        text = "off"
    }

    init {
        layoutParams = LayoutParams(sizeW, sizeH)
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF19466A.toInt(), 0xFF112D4A.toInt())
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 28f
            setStroke(3, 0x66D7F2FF)
        }
        foreground = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground)).run {
            val drawable = getDrawable(0)
            recycle()
            drawable
        }
        addView(
            stateTextView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        setOnClickListener {
            toggleListener?.invoke()
        }
    }

    fun setActive(active: Boolean) {
        stateTextView.text = if (active) "on" else "off"
    }
}
