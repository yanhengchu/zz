package cc.ai.zz.feature.automation.plan

object GestureRuntimeResolver {
    fun resolveNextCycleDelay(policy: NextCycleDelayPolicy, eventPeriodTime: Long): Long {
        return when (policy) {
            is NextCycleDelayPolicy.Fixed -> policy.delayMs
        }
    }
}
