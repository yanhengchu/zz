package cc.ai.zz.feature.ocr.rule

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrRuleTimeoutGateTest {
    @Test
    fun filterActiveRules_blocksSourceRuleBeforeTimeoutExpires() {
        var nowMs = 1_000L
        val gate = OcrRuleTimeoutGate(nowProvider = { nowMs })
        val sourceRule = createRule(
            id = "tree_planting_list_search",
            timeout = OcrRuleTimeout(awaitRuleId = "act_back", timeoutSeconds = 40)
        )
        val targetRule = createRule(id = "act_back")

        gate.onRuleExecuted(sourceRule, executed = true)

        val activeRuleIds = gate.filterActiveRules(listOf(sourceRule, targetRule)).map { it.id }

        assertEquals(listOf("act_back"), activeRuleIds)
        assertEquals(emptySet<String>(), gate.getTriggeredRuleIds())
    }

    @Test
    fun onRuleMatched_cancelsPendingTimeoutForAwaitedRule() {
        var nowMs = 1_000L
        val gate = OcrRuleTimeoutGate(nowProvider = { nowMs })
        val sourceRule = createRule(
            id = "tree_planting_list_search",
            timeout = OcrRuleTimeout(awaitRuleId = "act_back", timeoutSeconds = 40)
        )
        val targetRule = createRule(id = "act_back")

        gate.onRuleExecuted(sourceRule, executed = true)
        gate.onRuleMatched("act_back")

        val activeRuleIds = gate.filterActiveRules(listOf(sourceRule, targetRule)).map { it.id }

        assertEquals(listOf("tree_planting_list_search", "act_back"), activeRuleIds)
    }

    @Test
    fun filterActiveRules_restoresSourceRuleAfterTimeoutExpires() {
        var nowMs = 1_000L
        val gate = OcrRuleTimeoutGate(nowProvider = { nowMs })
        val sourceRule = createRule(
            id = "tree_planting_list_search",
            timeout = OcrRuleTimeout(awaitRuleId = "act_back", timeoutSeconds = 40)
        )
        val targetRule = createRule(id = "act_back")

        gate.onRuleExecuted(sourceRule, executed = true)
        nowMs += 40_000L

        val activeRuleIds = gate.filterActiveRules(listOf(sourceRule, targetRule)).map { it.id }

        assertEquals(listOf("act_back"), activeRuleIds)
        assertEquals(setOf("act_back"), gate.getTriggeredRuleIds())
    }

    private fun createRule(
        id: String,
        timeout: OcrRuleTimeout? = null
    ): OcrActionRule {
        return OcrActionRule(
            id = id,
            priority = 1,
            keywords = listOf(id),
            action = OcrRuleAction.Wait,
            timeout = timeout
        )
    }
}
