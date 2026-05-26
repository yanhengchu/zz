package cc.ai.zz.feature.automation.plan

import cc.ai.zz.feature.automation.command.GestureEvent

/**
 * GesturePlanFactory 负责把外部 GestureEvent 翻译成 GestureService 可执行的内部计划。
 *
 * 这样 GestureService 可以专注在调度与执行步骤，
 * 后续新增动作时主要在这里补新的 plan 构建逻辑。
 */
object GesturePlanFactory {
    // 初次启动/恢复后统一等待 5 秒再进入新一轮。
    const val INITIAL_DELAY_MS = 5_000L
    // 点击任务固定在返回后再等待 2.5 秒进入下一轮。
    const val CLICK_BACK_NEXT_CYCLE_DELAY_MS = 2_500L

    fun buildSwipeUpPlan(event: GestureEvent): GesturePlan {
        return GesturePlan(
            name = "开始固定${event.startTime.div(1000)}s上滑",
            initialDelayMs = INITIAL_DELAY_MS,
            nextCycleDelayPolicy = NextCycleDelayPolicy.Fixed(event.startTime),
            steps = listOf(
                GestureStep.SwipeUp()
            )
        )
    }

    fun buildClickBackPlan(event: GestureEvent): GesturePlan {
        return GesturePlan(
            name = "开始固定${event.startTime.div(1000)}s点击",
            initialDelayMs = INITIAL_DELAY_MS,
            nextCycleDelayPolicy = NextCycleDelayPolicy.Fixed(CLICK_BACK_NEXT_CYCLE_DELAY_MS),
            steps = listOf(
                // 当前策略是单次点击失败或点击位置缺失时只提示，不中断整个周期任务；
                // 即使本轮点击被跳过，后续返回和下一轮循环仍继续执行。
                GestureStep.ClickFromFloatingWindow(),
                GestureStep.Back(delayBeforeMs = event.startTime)
            )
        )
    }
}
