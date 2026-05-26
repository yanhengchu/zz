package cc.ai.zz.feature.ocr.rule

class OcrActionCooldownGate(
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val cooldownMs: Long = DEFAULT_ACTION_COOLDOWN_MS
) {
    companion object {
        const val DEFAULT_ACTION_COOLDOWN_MS = 5_000L
    }

    private val lastActionExecutedAtMsByRuleId = mutableMapOf<String, Long>()

    fun reset() {
        lastActionExecutedAtMsByRuleId.clear()
    }

    fun shouldBlock(rule: OcrActionRule): Boolean {
        if (!rule.action.requiresCooldown()) return false
        val lastActionTime = lastActionExecutedAtMsByRuleId[rule.id] ?: return false
        return nowProvider() - lastActionTime < cooldownMs
    }

    fun onRuleExecuted(rule: OcrActionRule, executed: Boolean) {
        if (!executed) return
        if (!rule.action.requiresCooldown()) return
        lastActionExecutedAtMsByRuleId[rule.id] = nowProvider()
    }

    private fun OcrRuleAction.requiresCooldown(): Boolean {
        return this is OcrRuleAction.Click || this is OcrRuleAction.Back
    }
}
