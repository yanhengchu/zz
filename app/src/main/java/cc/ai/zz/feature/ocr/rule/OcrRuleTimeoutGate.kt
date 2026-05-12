package cc.ai.zz.feature.ocr.rule

class OcrRuleTimeoutGate(
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    private data class PendingTimeout(
        val awaitRuleId: String,
        val expiresAtMs: Long,
        val isTriggered: Boolean = false
    )

    private val pendingBySourceRuleId = linkedMapOf<String, PendingTimeout>()

    fun reset() {
        pendingBySourceRuleId.clear()
    }

    fun filterActiveRules(rules: List<OcrActionRule>): List<OcrActionRule> {
        advanceExpiredTimeouts()
        if (pendingBySourceRuleId.isEmpty()) return rules
        return rules.filterNot { rule -> pendingBySourceRuleId.containsKey(rule.id) }
    }

    fun getTriggeredRuleIds(): Set<String> {
        advanceExpiredTimeouts()
        return pendingBySourceRuleId.values
            .asSequence()
            .filter { it.isTriggered }
            .map { it.awaitRuleId }
            .toSet()
    }

    fun onRuleMatched(ruleId: String) {
        advanceExpiredTimeouts()
        if (pendingBySourceRuleId.isEmpty()) return
        val iterator = pendingBySourceRuleId.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.awaitRuleId == ruleId) {
                iterator.remove()
            }
        }
    }

    fun onRuleExecuted(rule: OcrActionRule, executed: Boolean) {
        advanceExpiredTimeouts()
        if (!executed) return
        val timeout = rule.timeout ?: return
        pendingBySourceRuleId[rule.id] = PendingTimeout(
            awaitRuleId = timeout.awaitRuleId,
            expiresAtMs = nowProvider() + timeout.timeoutSeconds * 1000L
        )
    }

    private fun advanceExpiredTimeouts() {
        if (pendingBySourceRuleId.isEmpty()) return
        val now = nowProvider()
        val entriesToTrigger = mutableListOf<String>()
        pendingBySourceRuleId.forEach { (sourceRuleId, pending) ->
            if (!pending.isTriggered && pending.expiresAtMs <= now) {
                entriesToTrigger += sourceRuleId
            }
        }
        entriesToTrigger.forEach { sourceRuleId ->
            val pending = pendingBySourceRuleId[sourceRuleId] ?: return@forEach
            pendingBySourceRuleId[sourceRuleId] = pending.copy(isTriggered = true)
        }
    }
}
