package cc.ai.zz.feature.overlay.manager

import android.graphics.Point
import android.provider.Settings
import cc.ai.zz.app.MyApp
import cc.ai.zz.core.navigation.AppNavigator
import cc.ai.zz.feature.overlay.view.MainFloatingView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

object FloatingWindowManager {
    private val floatingScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var mainFloatingViewRef: WeakReference<MainFloatingView>? = null

    @Volatile
    private var isMainFloatingViewCreating = false

    fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(MyApp.context)
    }

    /** 主悬浮窗 */
    fun tryShow(timeLimitMs: Long? = null) = floatingScope.launch {
        // 悬浮窗已经展示，则不重复展示
        val existingView = mainFloatingViewRef?.get()
        if (existingView != null) {
            timeLimitMs?.let { existingView.updateTimeLimit(it) }
            return@launch
        }
        if (isMainFloatingViewCreating) return@launch
        isMainFloatingViewCreating = true
        // 获取上下文
        try {
            val context = MyApp.context
            // 悬浮窗权限检查
            if (!Settings.canDrawOverlays(context)) return@launch
            // 双重检查，避免等待期间其他调用已经创建完成
            val latestView = mainFloatingViewRef?.get()
            if (latestView != null) {
                timeLimitMs?.let { latestView.updateTimeLimit(it) }
                return@launch
            }
            // 创建悬浮窗
            val view = MainFloatingView(context)
            view.show()
            // 双击
            view.doubleClickListener = {
                AppNavigator.openHome()
            }
            mainFloatingViewRef = WeakReference(view)
            timeLimitMs?.let { view.updateTimeLimit(it) }
        } finally {
            isMainFloatingViewCreating = false
        }
    }

    fun updateTimeLimit(timeLimitMs: Long) {
        mainFloatingViewRef?.get()?.updateTimeLimit(timeLimitMs)
    }

    fun tryHide() = floatingScope.launch {
        mainFloatingViewRef?.get()?.let { view ->
            view.stopCountdown()
            view.hide()
        }
        mainFloatingViewRef = null
    }

    fun setMainOverlayVisible(visible: Boolean) {
        mainFloatingViewRef?.get()?.setOverlayVisible(visible)
    }

    /**
     * 获取主悬浮窗的左上角位置。
     * 当前正在向“统一控制悬浮窗”演进，后续点击锚点将逐步以主悬浮窗位置为准。
     */
    fun getMainPosition(): Point? {
        val mainView = mainFloatingViewRef?.get() ?: return null
        return mainView.getScreenPosition()
    }
}
