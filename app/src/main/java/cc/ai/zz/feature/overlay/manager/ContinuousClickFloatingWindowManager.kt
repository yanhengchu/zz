package cc.ai.zz.feature.overlay.manager

import android.graphics.Point
import cc.ai.zz.app.MyApp
import cc.ai.zz.feature.automation.command.GestureEvent
import cc.ai.zz.feature.automation.command.emit
import cc.ai.zz.feature.automation.service.GestureService
import cc.ai.zz.feature.overlay.view.ContinuousClickFloatingView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

object ContinuousClickFloatingWindowManager {
    private val floatingScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var clickViewRef: WeakReference<ContinuousClickFloatingView>? = null

    @Volatile
    private var isCreating = false

    fun isShowing(): Boolean = clickViewRef?.get() != null

    fun tryShow() = floatingScope.launch {
        if (clickViewRef?.get() != null) return@launch
        if (isCreating) return@launch
        isCreating = true
        try {
            if (!FloatingWindowManager.hasOverlayPermission()) return@launch
            if (clickViewRef?.get() != null) return@launch
            val view = ContinuousClickFloatingView(MyApp.context)
            view.setActive(false)
            view.toggleListener = {
                GestureEvent.ACT_TOGGLE_CONTINUOUS_CLICK.emit()
            }
            view.show()
            clickViewRef = WeakReference(view)
        } finally {
            isCreating = false
        }
    }

    fun tryHide() = floatingScope.launch {
        if (GestureService.isContinuousClickActive) {
            GestureEvent.ACT_STOP_CONTINUOUS_CLICK.emit()
        }
        clickViewRef?.get()?.hide()
        clickViewRef = null
    }

    fun updateActive(active: Boolean) {
        clickViewRef?.get()?.setActive(active)
    }

    fun getClickAnchorPosition(): Point? {
        return clickViewRef?.get()?.getScreenPosition()
    }
}
