package cc.ai.zz.feature.overlay.manager

import cc.ai.zz.app.MyApp
import cc.ai.zz.feature.overlay.view.CoordinateLocatorFloatingView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

object CoordinateLocatorFloatingWindowManager {
    private val floatingScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var locatorViewRef: WeakReference<CoordinateLocatorFloatingView>? = null

    @Volatile
    private var isCreating = false

    fun isShowing(): Boolean = locatorViewRef?.get() != null

    fun tryShow() = floatingScope.launch {
        if (locatorViewRef?.get() != null) return@launch
        if (isCreating) return@launch
        isCreating = true
        try {
            if (!FloatingWindowManager.hasOverlayPermission()) return@launch
            if (locatorViewRef?.get() != null) return@launch
            val view = CoordinateLocatorFloatingView(MyApp.context)
            view.show()
            locatorViewRef = WeakReference(view)
        } finally {
            isCreating = false
        }
    }

    fun tryHide() = floatingScope.launch {
        locatorViewRef?.get()?.hide()
        locatorViewRef = null
    }
}
