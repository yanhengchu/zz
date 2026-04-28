package cc.ai.zz.feature.automation.command

import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import cc.ai.zz.app.MyApp
import cc.ai.zz.feature.automation.service.GestureService

interface GestureTask {
    fun emitGesture(event: GestureEvent, configureIntent: Intent.() -> Unit = {})
}

/** 周期任务管理器 统一管理周期任务的启动和停止 */
class GestureTaskImpl : GestureTask {
    companion object {
        private const val TAG = "GestureTask"
    }

    override fun emitGesture(event: GestureEvent, configureIntent: Intent.() -> Unit) {
        val context = MyApp.context
        val intent = Intent(context, GestureService::class.java)
        intent.putExtra("GestureEvent", event)
        intent.configureIntent()
        Log.d(TAG, "emit action=${event.action} startTime=${event.startTime}")
        when (event.action) {
            GestureEvent.ACT_STOP -> context.startService(intent)
            GestureEvent.ACT_START_OCR -> ContextCompat.startForegroundService(context, intent)
            else -> ContextCompat.startForegroundService(context, intent)
        }
    }
}

object GestureTaskManager : GestureTask by GestureTaskImpl()

/**
 * 统一的命令发送入口：
 * - 外部调用方统一走 `GestureEvent.xxx.emit(...)`
 * - 如需附加参数，通过 configureIntent 补充 extras
 * - 不直接在调用方手写 GestureTaskManager.emitGesture(...)，避免再次分叉出第二套调用方式
 */
fun String.emit(startTime: Long = 0, configureIntent: Intent.() -> Unit = {}) = GestureTaskManager.emitGesture(
    event = GestureEvent(action = this, startTime = startTime), configureIntent = configureIntent
)
