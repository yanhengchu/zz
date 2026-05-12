package cc.ai.zz.feature.automation.command

import java.io.Serializable

/**
 * GestureEvent 是外部命令模型：
 * Activity、悬浮窗等入口通过它把“要做什么”发送给 GestureService。
 *
 * 当前项目约定所有外部命令都统一通过 GestureEvent.action 分发。
 * 如果某个命令还需要额外参数（例如 OCR 授权结果），通过 Intent extras 补充，
 * 但服务侧仍然只以 GestureEvent.action 作为唯一命令入口。
 *
 * 它只描述外部命令和基础参数，不负责服务内部如何把命令拆成执行步骤。
 */
class GestureEvent(
    val action: String, var startTime: Long = 0, var periodTime: Long = 0, val x: Float = 0f, val y: Float = 0f
) : Serializable {
    companion object {
        const val GESTURE_EVENT_DURATION = 300L
        const val PERIOD_MS = 5000L

        /** 停止当前周期任务和当前 OCR；不默认关闭服务宿主。 */
        const val ACT_STOP = "ACT_STOP"

        /** 立即执行一次返回。 */
        const val ACT_BACK = "ACT_BACK"

        /** 开始周期上滑任务。 */
        const val ACT_SWIPE_UP = "ACT_SWIPE_UP"

        /** 开始“点击后返回”的周期任务。 */
        const val ACT_CLICK_BACK = "ACT_CLICK_BACK"

        /** 开启 OCR；授权结果通过 Intent extras 附带。 */
        const val ACT_START_OCR = "ACT_START_OCR"
    }
}
