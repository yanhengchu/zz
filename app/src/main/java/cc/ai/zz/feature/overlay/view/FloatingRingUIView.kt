package cc.ai.zz.feature.overlay.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import java.util.Locale

abstract class FloatingRingUIView
@JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    BaseFloatingView(context, attrs, defStyleAttr) {
    private val cardPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    }
    private val borderPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = 0x66D7F2FF
        }
    }
    private val glowPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0x2A7EE6FF
            maskFilter = android.graphics.BlurMaskFilter(20f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
    }
    private val anchorPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFFF7C948.toInt()
        }
    }
    private val anchorStrokePaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = 0xFF0E223A.toInt()
        }
    }
    private val countdownTextPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFFFFFFFF.toInt()
            textAlign = Paint.Align.CENTER
            textSize = 38f
            isFakeBoldText = true
        }
    }
    private val hintTextPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFFD7E7F5.toInt()
            textAlign = Paint.Align.CENTER
            textSize = 12f
            isFakeBoldText = true
        }
    }

    private var countdownText: String? = null
    private val cardRect = RectF()
    private val glowRect = RectF()
    private val anchorTriangle = Path()
    private var cardGradient: LinearGradient? = null

    init {
        setWillNotDraw(false)
        layoutParams = LayoutParams(sizeW, sizeH)
        // 软件层可让 glow 模糊生效，悬浮控件尺寸较小，额外开销可接受。
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        glowRect.set(10f, 16f, w - 10f, h - 10f)
        cardRect.set(18f, 22f, w - 18f, h - 18f)
        cardGradient = LinearGradient(
            cardRect.left,
            cardRect.top,
            cardRect.right,
            cardRect.bottom,
            intArrayOf(0xFF19466A.toInt(), 0xFF112D4A.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
        cardPaint.shader = cardGradient

        anchorTriangle.reset()
        anchorTriangle.moveTo(cardRect.left - 12f, cardRect.top - 12f)
        anchorTriangle.lineTo(cardRect.left + 16f, cardRect.top + 6f)
        anchorTriangle.lineTo(cardRect.left + 6f, cardRect.top + 16f)
        anchorTriangle.close()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val cornerRadius = 24f

        canvas.drawRoundRect(glowRect, cornerRadius + 8f, cornerRadius + 8f, glowPaint)
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, cardPaint)
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, borderPaint)

        canvas.drawPath(anchorTriangle, anchorPaint)
        canvas.drawCircle(cardRect.left + 10f, cardRect.top + 10f, 7f, anchorPaint)
        canvas.drawLine(cardRect.left + 10f, cardRect.top, cardRect.left + 10f, cardRect.top + 20f, anchorStrokePaint)
        canvas.drawLine(cardRect.left, cardRect.top + 10f, cardRect.left + 20f, cardRect.top + 10f, anchorStrokePaint)

        val text = countdownText
        if (text != null) {
            val timerTextY = cy + 4f
            canvas.drawText(text, cx, timerTextY, countdownTextPaint)
            val hintTextY = cardRect.bottom - 16f
            canvas.drawText("open drag", cx, hintTextY, hintTextPaint)
        }
    }

    protected fun updateCountdownText(timeSeconds: Long?) {
        countdownText = timeSeconds?.let { String.format(Locale.US, "%02d", it) }
        invalidate()
    }
}
