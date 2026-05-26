package cc.ai.zz.feature.automation.plan

/**
 * GesturePlan 是 GestureService 内部使用的执行计划模型。
 *
 * 它和 GestureEvent 的职责不同：
 * - GestureEvent：外部发送给服务的命令
 * - GesturePlan：服务把命令翻译成的内部执行步骤
 *
 * 当前设计里 GesturePlan 本身相对稳定，后续新增能力时主要扩展 GestureStep。
 */
data class GesturePlan(
    val name: String,
    val initialDelayMs: Long,
    val nextCycleDelayPolicy: NextCycleDelayPolicy,
    val failurePolicy: FailurePolicy = FailurePolicy.ToastOnlyContinue,
    val steps: List<GestureStep>
)

/**
 * 决定一轮步骤执行结束后，下一轮应该在多久之后开始。
 */
sealed interface NextCycleDelayPolicy {
    /**
     * 下一轮间隔取当前 event.periodTime。
     * 适合像上滑这样会在执行过程中动态调整下一轮周期的动作。
     */
    data object UseEventPeriodTime : NextCycleDelayPolicy

    /**
     * 下一轮间隔固定为某个值。
     * 适合像点击后返回这样分阶段固定推进的动作。
     */
    data class Fixed(val delayMs: Long) : NextCycleDelayPolicy
}

/**
 * 定义单次步骤执行失败时，整个周期任务应该如何处理。
 */
sealed interface FailurePolicy {
    /**
     * 仅提示失败，但继续后续步骤或下一轮周期。
     * 当前项目把用户抢占、页面切换、系统临时取消等情况都归入这一类。
     */
    data object ToastOnlyContinue : FailurePolicy
}

/**
 * GestureStep 描述单轮任务中的一个步骤。
 *
 * delayBeforeMs 表示执行当前步骤前需要等待多久，
 * 这样可以把“点击后等待一段时间再返回”这种两阶段动作也表示成一组步骤。
 */
sealed interface GestureStep {
    val delayBeforeMs: Long

    data class SwipeUp(
        override val delayBeforeMs: Long = 0L
    ) : GestureStep

    data class ClickFromFloatingWindow(
        override val delayBeforeMs: Long = 0L,
        val offsetX: Int = -10,
        val offsetY: Int = -10
    ) : GestureStep

    data class Back(
        override val delayBeforeMs: Long = 0L
    ) : GestureStep
}
