package cc.ai.zz.feature.ocr.rule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrDynamicValueGateTest {
    private val gate = OcrDynamicValueGate()

    @Test
    fun shouldExecute_withChangedPolicy_runsOnFirstAndChangedValues() {
        val rule = createRule(valuePolicy = OcrValuePolicy.Changed)

        val first = gate.evaluate(match(rule, "120:30"))
        assertTrue(first.shouldExecute)
        gate.observe(first)
        gate.onActionResult(first, executed = true)

        val second = gate.evaluate(match(rule, "120:30"))
        assertFalse(second.shouldExecute)
        gate.observe(second)

        val third = gate.evaluate(match(rule, "120:28"))
        assertTrue(third.shouldExecute)
    }

    @Test
    fun shouldExecute_withUnchangedPolicy_runsOnFirstAndThenOnlyWhenSame() {
        val rule = createRule(valuePolicy = OcrValuePolicy.Unchanged)

        val first = gate.evaluate(match(rule, "120:30"))
        assertTrue(first.shouldExecute)
        gate.observe(first)
        gate.onActionResult(first, executed = true)

        val second = gate.evaluate(match(rule, "120:30"))
        assertTrue(second.shouldExecute)
        gate.observe(second)
        gate.onActionResult(second, executed = true)

        val third = gate.evaluate(match(rule, "120:28"))
        assertFalse(third.shouldExecute)
        gate.observe(third)

        val fourth = gate.evaluate(match(rule, "120:28"))
        assertTrue(fourth.shouldExecute)
    }

    @Test
    fun shouldExecute_fallsBackToOrdinaryExecutionWhenDynamicValueMissing() {
        val rule = createRule(valuePolicy = OcrValuePolicy.Changed)

        assertTrue(gate.evaluate(match(rule, null)).shouldExecute)
        assertTrue(gate.evaluate(match(rule, null)).shouldExecute)
    }

    @Test
    fun reset_clearsPreviousDynamicValue() {
        val rule = createRule(valuePolicy = OcrValuePolicy.Changed)

        val first = gate.evaluate(match(rule, "120:30"))
        assertTrue(first.shouldExecute)
        gate.observe(first)
        gate.onActionResult(first, executed = true)

        val second = gate.evaluate(match(rule, "120:30"))
        assertFalse(second.shouldExecute)

        gate.reset()

        assertTrue(gate.evaluate(match(rule, "120:30")).shouldExecute)
    }

    @Test
    fun commit_onlyAdvancesStateWhenExplicitlyCalled() {
        val rule = createRule(valuePolicy = OcrValuePolicy.Changed)

        val first = gate.evaluate(match(rule, "120:30"))
        assertTrue(first.shouldExecute)
        assertTrue(gate.peek(rule.id) == null)

        val second = gate.evaluate(match(rule, "120:30"))
        assertTrue(second.shouldExecute)

        gate.observe(first)

        val third = gate.evaluate(match(rule, "120:30"))
        assertFalse(third.shouldExecute)
    }

    @Test
    fun onActionResult_keepsPendingRetryForSameValueWhenExecutionFails() {
        val rule = createRule(valuePolicy = OcrValuePolicy.Changed)

        val first = gate.evaluate(match(rule, "120:30"))
        gate.observe(first)
        gate.onActionResult(first, executed = false)

        val retry = gate.evaluate(match(rule, "120:30"))
        assertTrue(retry.shouldExecute)
    }

    @Test
    fun evaluate_numericThreshold_usesElseTargetWhenConditionDoesNotMatch() {
        val rule = OcrActionRule(
            id = "ad_next",
            priority = 10,
            keywords = listOf("再看一个视频继续领奖励", "num金币"),
            action = OcrRuleAction.Click(
                target = OcrClickTarget(0.5f, 0.56f),
                elseTarget = OcrClickTarget(0.82f, 0.56f)
            ),
            valuePolicy = OcrValuePolicy.NumericThreshold(NumericCompareOperator.LT, 300)
        )

        val fallback = gate.evaluate(match(rule, "568"))
        val matched = gate.evaluate(match(rule, "120"))

        assertTrue(matched.shouldExecute)
        assertFalse(matched.useElseTarget)
        assertTrue(fallback.shouldExecute)
        assertTrue(fallback.useElseTarget)
    }

    private fun createRule(valuePolicy: OcrValuePolicy): OcrActionRule {
        return OcrActionRule(
            id = "video_swipe",
            priority = 10,
            keywords = listOf("首页", "倒计时mm:ss"),
            action = OcrRuleAction.Swipe,
            valuePolicy = valuePolicy
        )
    }

    private fun match(rule: OcrActionRule, dynamicValue: String?): OcrRuleMatcher.OcrRuleMatch {
        return OcrRuleMatcher.OcrRuleMatch(
            rule = rule,
            dynamicValue = dynamicValue
        )
    }
}
