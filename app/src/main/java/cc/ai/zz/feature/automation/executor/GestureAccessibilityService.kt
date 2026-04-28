package cc.ai.zz.feature.automation.executor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Path
import android.provider.Settings
import com.hjq.toast.Toaster
import cc.ai.zz.feature.automation.command.GestureEvent

class GestureAccessibilityService : AccessibilityService() {
    companion object {
        var instance: GestureAccessibilityService? = null

        fun checkAccessibilityServiceDisabled(context: Activity, autoNavigate: Boolean = true): Boolean {
            var disabled = true
            if (Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1) {
                val settingValue = Settings.Secure.getString(
                    context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                disabled = !isAccessibilityServiceEnabled(context, settingValue)
            }
            if (disabled && autoNavigate) {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            return disabled
        }

        private fun isAccessibilityServiceEnabled(context: Activity, enabledServices: String?): Boolean {
            if (enabledServices.isNullOrBlank()) return false
            val expectedService = ComponentName(context, GestureAccessibilityService::class.java)
            return enabledServices.split(':').any { item ->
                ComponentName.unflattenFromString(item) == expectedService
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
    }

    override fun onInterrupt() {
    }

    fun executeSwipeUp() {
        // 屏幕信息
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        // 手势路径
        val path = Path()
        val startY = screenHeight * 0.7f
        val endY = screenHeight * 0.3f
        val centerX = screenWidth / 2f
        path.moveTo(centerX, startY)
        path.lineTo(centerX, endY)
        // 创建手势描述
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, GestureEvent.GESTURE_EVENT_DURATION))
        dispatchGestureWithFeedback(gestureBuilder.build(), "上滑失败")
    }

    fun executeClick(x: Float? = null, y: Float? = null) {
        // 屏幕信息
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        // 点击位置，如果未指定则使用屏幕中心
        val clickX = x ?: (screenWidth / 2f)
        val clickY = y ?: (screenHeight / 2f)
        // 手势路径：点击就是在同一位置创建一个非常短的路径
        val path = Path()
        path.moveTo(clickX, clickY)
        path.lineTo(clickX, clickY)
        // 创建手势描述，点击持续时间很短
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 50))
        dispatchGestureWithFeedback(gestureBuilder.build(), "点击失败")
    }

    /**
     * AccessibilityService 可用的全局操作 API（performGlobalAction）：
     *
     * 基础操作（所有版本）：
     * - GLOBAL_ACTION_BACK             返回上一页
     * - GLOBAL_ACTION_HOME             回到主屏幕
     * - GLOBAL_ACTION_RECENTS          显示最近任务列表
     * - GLOBAL_ACTION_NOTIFICATIONS    打开通知栏
     * - GLOBAL_ACTION_QUICK_SETTINGS   打开快速设置面板
     * - GLOBAL_ACTION_POWER_DIALOG     打开电源对话框
     *
     * Android 7.0+ (API 24+)：
     * - GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN  切换分屏模式
     *
     * Android 9.0+ (API 28+)：
     * - GLOBAL_ACTION_LOCK_SCREEN      锁定屏幕
     *
     * Android 11+ (API 30+)：
     * - GLOBAL_ACTION_TAKE_SCREENSHOT          截屏
     * - GLOBAL_ACTION_KEYCODE_HEADSETHOOK     耳机按键
     * - GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS  打开所有应用
     * - GLOBAL_ACTION_ACCESSIBILITY_SHORTCUT  打开无障碍快捷方式
     * - GLOBAL_ACTION_ACCESSIBILITY_BUTTON    打开无障碍按钮
     * - GLOBAL_ACTION_ACCESSIBILITY_BUTTON_CHOOSER  打开无障碍按钮选择器
     * - GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE    关闭通知栏
     * - GLOBAL_ACTION_DPAD_UP/DOWN/LEFT/RIGHT/CENTER  方向键导航
     */
    fun executeBack() {
        // 方案1：使用系统返回API（推荐，最可靠）
        val success = performGlobalAction(GLOBAL_ACTION_BACK)
        if (success) {
            return
        }

        // 方案2：如果系统API失败，尝试手势模拟
        // 屏幕信息
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        // 手势路径：从屏幕边缘内侧开始，向左滑动更长的距离
        val path = Path()
        val centerY = screenHeight / 2f
        // 从屏幕边缘内侧10像素开始（系统返回手势通常需要从边缘区域触发）
        val startX = (screenWidth - 10).toFloat()
        // 向左滑动到屏幕中间位置（增加滑动距离以提高触发成功率）
        val endX = screenWidth / 2f
        path.moveTo(startX, centerY)
        path.lineTo(endX, centerY)
        // 创建手势描述，使用稍长的持续时间
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, GestureEvent.GESTURE_EVENT_DURATION))
        dispatchGestureWithFeedback(gestureBuilder.build(), "返回失败")
    }

    private fun dispatchGestureWithFeedback(
        gesture: GestureDescription,
        failureMessage: String
    ) {
        // 执行层只在失败时主动提示；
        // 成功动作通常能直接从页面变化中观察到，避免高频周期任务反复 toast 打扰用户。
        // 是否停止后续周期任务由上层调度决定，当前策略是不因单次失败自动停任务。
        val dispatched = dispatchGesture(
            gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Toaster.show(failureMessage)
                }
            }, null
        )
        if (!dispatched) {
            Toaster.show(failureMessage)
        }
    }
}
