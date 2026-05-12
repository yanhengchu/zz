package cc.ai.zz.feature.overlay.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import cc.ai.zz.feature.overlay.store.OverlayPositionKey
import cc.ai.zz.feature.overlay.store.OverlayPositionStore

@SuppressLint("ClickableViewAccessibility")
abstract class BaseFloatingView
@JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    FrameLayout(context, attrs, defStyleAttr) {
    private val windowManager by lazy {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    private val positionStore = OverlayPositionStore()
    private var lastX = 0
    private var lastY = 0
    private var paramX = 0
    private var paramY = 0
    private var isAttachedToWindowManager = false

    protected val sizeW = 120
    protected val sizeH = 120
    protected abstract val positionKey: OverlayPositionKey

    var doubleClickListener: (() -> Unit)? = null

    // 手势相关
    private val gestureDetector: GestureDetector by lazy {
        GestureDetector(
            context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    performClick()
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    doubleClickListener?.invoke()
                    return true
                }
            })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 优先让手势识别器处理
        if (gestureDetector.onTouchEvent(event)) return true
        // 拖拽逻辑
        val params = layoutParams as? WindowManager.LayoutParams ?: return true
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.rawX.toInt()
                lastY = event.rawY.toInt()
                paramX = params.x
                paramY = params.y
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX.toInt() - lastX
                val dy = event.rawY.toInt() - lastY
                params.x = paramX + dx
                params.y = paramY + dy
                // 更新悬浮窗位置
                windowManager.updateViewLayout(this, params)
                onPositionChanged(params.x, params.y, false)
            }

            MotionEvent.ACTION_UP -> {
                // 保存悬浮窗位置
                positionStore.savePosition(positionKey.xKey, positionKey.yKey, params.x, params.y)
                onPositionChanged(params.x, params.y, true)
            }
        }
        return true
    }

    fun show() {
        showOnMain()
    }

    fun hide() {
        hideOnMain()
    }

    fun showOnMain() {
        if (isAttachedToWindowManager) return
        val params = WindowManager.LayoutParams(
            sizeW, // 宽
            sizeH, // 高
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        // 设置 gravity 为左上角，这样 x 和 y 就是相对于屏幕左上角的坐标
        params.gravity = Gravity.TOP or Gravity.START
        // 恢复悬浮窗位置
        val savedPosition = positionStore.readPosition(positionKey.xKey, positionKey.yKey)
        params.x = savedPosition.x
        params.y = savedPosition.y
        windowManager.addView(this, params)
        isAttachedToWindowManager = true
        onPositionChanged(params.x, params.y, true)
    }

    fun hideOnMain() {
        if (!isAttachedToWindowManager) return
        windowManager.removeView(this)
        isAttachedToWindowManager = false
    }

    fun setOverlayVisible(visible: Boolean) {
        visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    /**
     * 获取悬浮窗口在屏幕上的位置（左上角）
     * 优先使用 getLocationOnScreen()，如果失败则从 LayoutParams 获取
     * @return Point 包含 x 和 y 坐标，如果无法获取则返回 null
     */
    fun getScreenPosition(): Point? {
        // 方法1：使用 getLocationOnScreen() 获取屏幕绝对坐标（最可靠）
        try {
            val location = IntArray(2)
            getLocationOnScreen(location)
            // 如果坐标有效（非负值或合理的值），返回它
            if (location[0] >= 0 && location[1] >= 0) {
                return Point(location[0], location[1])
            }
        } catch (e: Exception) {
            // 如果 getLocationOnScreen() 失败，继续尝试其他方法
        }

        // 方法2：从 WindowManager.LayoutParams 获取
        val params = layoutParams as? WindowManager.LayoutParams ?: return null
        // 如果设置了 gravity 为 TOP|START，x 和 y 就是相对于屏幕左上角的坐标
        val gravity = params.gravity
        if (gravity == (Gravity.TOP or Gravity.START) || gravity == 0) {
            // gravity 为 TOP|START 或未设置（默认为 TOP|START），直接使用 x 和 y
            return Point(params.x, params.y)
        } else {
            // 如果 gravity 不同，需要根据 gravity 计算
            // 这里简化处理，直接返回 x 和 y（可能需要根据实际情况调整）
            return Point(params.x, params.y)
        }
    }

    /**
     * 获取悬浮窗口中心点在屏幕上的位置
     * 用于点击操作，坐标系统与 executeClick() 一致（相对于屏幕左上角）
     * @return Point 包含中心点的 x 和 y 坐标，如果无法获取则返回 null
     */
    fun getScreenCenterPosition(): Point? {
        val topLeft = getScreenPosition() ?: return null
        // 计算中心点：左上角坐标 + 宽度/2 和 高度/2
        return Point(topLeft.x + sizeW / 2, topLeft.y + sizeH / 2)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isAttachedToWindowManager = false
    }

    protected open fun onPositionChanged(screenX: Int, screenY: Int, isFinal: Boolean) {
    }
}
