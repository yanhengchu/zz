package cc.ai.zz.feature.overlay.manager

import android.provider.Settings
import cc.ai.zz.app.MyApp
import cc.ai.zz.feature.overlay.view.OcrStatusFloatingView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

object OcrStatusFloatingWindowManager {
    private val floatingScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var viewRef: WeakReference<OcrStatusFloatingView>? = null
    @Volatile
    private var isCreating = false

    fun tryShow(status: String = "boot") = floatingScope.launch {
        val existingView = viewRef?.get()
        if (existingView != null) {
            existingView.updateStatus(status)
            return@launch
        }
        if (isCreating) return@launch
        isCreating = true
        try {
            val context = MyApp.context
            if (!Settings.canDrawOverlays(context)) return@launch
            val latestView = viewRef?.get()
            if (latestView != null) {
                latestView.updateStatus(status)
                return@launch
            }
            val view = OcrStatusFloatingView(context)
            view.show()
            view.updateStatus(status)
            viewRef = WeakReference(view)
        } finally {
            isCreating = false
        }
    }

    fun updateStatus(status: String) = floatingScope.launch {
        viewRef?.get()?.updateStatus(status)
    }

    fun tryHide() = floatingScope.launch {
        viewRef?.get()?.hide()
        viewRef = null
    }
}
