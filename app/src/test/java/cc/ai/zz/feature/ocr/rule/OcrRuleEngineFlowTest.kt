package cc.ai.zz.feature.ocr.rule

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrRuleEngineFlowTest {
    @Test
    fun handleRecognizedTextWithRules_executesNextRuleWhenSamePriorityFirstRuleSkips() {
        val matcher = OcrRuleMatcher()
        val timeoutGate = OcrRuleTimeoutGate()
        val actionCooldownGate = OcrActionCooldownGate()
        val dynamicValueGate = OcrDynamicValueGate()
        val executedRuleIds = mutableListOf<String>()
        val rules = listOf(
            OcrActionRule(
                id = "tree_planting_list_ad",
                priority = 0,
                keywords = listOf("看广告视频", "看30秒广告可领水滴今日还剩num次"),
                action = OcrRuleAction.Click(OcrClickTarget(0.5f, 0.5f)),
                valuePolicy = OcrValuePolicy.NumericThreshold(NumericCompareOperator.GT, 0)
            ),
            OcrActionRule(
                id = "tree_planting_list_search",
                priority = 0,
                keywords = listOf("浏览搜索商品", "浏览30秒可领水滴今日还剩num次"),
                action = OcrRuleAction.Click(OcrClickTarget(0.5f, 0.6f)),
                valuePolicy = OcrValuePolicy.NumericThreshold(NumericCompareOperator.GT, 0)
            )
        )

        val result = handleRecognizedTextWithRules(
            rules = rules,
            packageName = "",
            text = "看广告视频 看30秒广告可领水滴今日还剩0次 浏览搜索商品 浏览30秒可领水滴今日还剩3次",
            matcher = matcher,
            timeoutGate = timeoutGate,
            actionCooldownGate = actionCooldownGate,
            dynamicValueGate = dynamicValueGate,
            executeRule = { rule, _ ->
                executedRuleIds += rule.id
                true
            },
            logClickDecision = { _, _, _ -> },
            onLog = { }
        )

        assertEquals("tree_planting_list_search", result.status)
        assertEquals(listOf("tree_planting_list_search"), executedRuleIds)
    }

    @Test
    fun handleRecognizedTextWithRules_matchesTimeoutAllAfterTimeoutExpires() {
        var nowMs = 1_000L
        val matcher = OcrRuleMatcher()
        val timeoutGate = OcrRuleTimeoutGate(nowProvider = { nowMs })
        val actionCooldownGate = OcrActionCooldownGate(nowProvider = { nowMs })
        val dynamicValueGate = OcrDynamicValueGate()
        val executedRuleIds = mutableListOf<String>()
        val rules = listOf(
            OcrActionRule(
                id = "tree_planting_list_search",
                priority = 0,
                keywords = listOf("浏览搜索商品"),
                action = OcrRuleAction.Click(OcrClickTarget(0.5f, 0.5f)),
                timeout = OcrRuleTimeout(awaitRuleId = "timeout_back", timeoutSeconds = 40)
            ),
            OcrActionRule(
                id = "timeout_back",
                priority = 0,
                keywords = listOf("TIMEOUT_ALL"),
                action = OcrRuleAction.Back
            )
        )

        val first = handleRecognizedTextWithRules(
            rules = rules,
            packageName = "",
            text = "浏览搜索商品",
            matcher = matcher,
            timeoutGate = timeoutGate,
            actionCooldownGate = actionCooldownGate,
            dynamicValueGate = dynamicValueGate,
            executeRule = { rule, _ ->
                executedRuleIds += rule.id
                true
            },
            logClickDecision = { _, _, _ -> },
            onLog = { }
        )
        nowMs += 40_000L
        val second = handleRecognizedTextWithRules(
            rules = rules,
            packageName = "",
            text = "任意OCR结果",
            matcher = matcher,
            timeoutGate = timeoutGate,
            actionCooldownGate = actionCooldownGate,
            dynamicValueGate = dynamicValueGate,
            executeRule = { rule, _ ->
                executedRuleIds += rule.id
                true
            },
            logClickDecision = { _, _, _ -> },
            onLog = { }
        )

        assertEquals("tree_planting_list_search", first.status)
        assertEquals("timeout_back", second.status)
        assertEquals(listOf("tree_planting_list_search", "timeout_back"), executedRuleIds)
    }

    @Test
    fun handleRecognizedTextWithRules_displaysActionSuffixForClickRuleWithElseTarget() {
        val matcher = OcrRuleMatcher()
        val timeoutGate = OcrRuleTimeoutGate()
        val actionCooldownGate = OcrActionCooldownGate()
        val dynamicValueGate = OcrDynamicValueGate()
        val rules = listOf(
            OcrActionRule(
                id = "ad_next",
                priority = 0,
                keywords = listOf("num金币", "继续领奖励"),
                action = OcrRuleAction.Click(
                    target = OcrClickTarget(0.5f, 0.5f),
                    elseTarget = OcrClickTarget(0.6f, 0.6f)
                ),
                valuePolicy = OcrValuePolicy.NumericThreshold(NumericCompareOperator.GT, 300)
            )
        )

        val result = handleRecognizedTextWithRules(
            rules = rules,
            packageName = "",
            text = "568金币 继续领奖励",
            matcher = matcher,
            timeoutGate = timeoutGate,
            actionCooldownGate = actionCooldownGate,
            dynamicValueGate = dynamicValueGate,
            executeRule = { _, _ -> true },
            logClickDecision = { _, _, _ -> },
            onLog = { }
        )

        assertEquals("ad_next/action", result.status)
    }

    @Test
    fun handleRecognizedTextWithRules_displaysElseSuffixWhenElseTargetIsUsed() {
        val matcher = OcrRuleMatcher()
        val timeoutGate = OcrRuleTimeoutGate()
        val actionCooldownGate = OcrActionCooldownGate()
        val dynamicValueGate = OcrDynamicValueGate()
        val rules = listOf(
            OcrActionRule(
                id = "ad_next",
                priority = 0,
                keywords = listOf("num金币", "继续领奖励"),
                action = OcrRuleAction.Click(
                    target = OcrClickTarget(0.5f, 0.5f),
                    elseTarget = OcrClickTarget(0.6f, 0.6f)
                ),
                valuePolicy = OcrValuePolicy.NumericThreshold(NumericCompareOperator.GT, 300)
            )
        )

        val result = handleRecognizedTextWithRules(
            rules = rules,
            packageName = "",
            text = "198金币 继续领奖励",
            matcher = matcher,
            timeoutGate = timeoutGate,
            actionCooldownGate = actionCooldownGate,
            dynamicValueGate = dynamicValueGate,
            executeRule = { _, _ -> true },
            logClickDecision = { _, _, _ -> },
            onLog = { }
        )

        assertEquals("ad_next/else", result.status)
    }

    @Test
    fun handleRecognizedTextWithRules_blocksRepeatedClickWithinCooldownWindow() {
        var nowMs = 1_000L
        val matcher = OcrRuleMatcher()
        val timeoutGate = OcrRuleTimeoutGate()
        val actionCooldownGate = OcrActionCooldownGate(nowProvider = { nowMs })
        val dynamicValueGate = OcrDynamicValueGate()
        val executedRuleIds = mutableListOf<String>()
        val rules = listOf(
            OcrActionRule(
                id = "ad_next",
                priority = 0,
                keywords = listOf("继续领奖励"),
                action = OcrRuleAction.Click(OcrClickTarget(0.5f, 0.5f))
            )
        )

        val first = handleRecognizedTextWithRules(
            rules = rules,
            packageName = "",
            text = "继续领奖励",
            matcher = matcher,
            timeoutGate = timeoutGate,
            actionCooldownGate = actionCooldownGate,
            dynamicValueGate = dynamicValueGate,
            executeRule = { rule, _ ->
                executedRuleIds += rule.id
                true
            },
            logClickDecision = { _, _, _ -> },
            onLog = { }
        )

        nowMs += 2_000L
        val second = handleRecognizedTextWithRules(
            rules = rules,
            packageName = "",
            text = "继续领奖励",
            matcher = matcher,
            timeoutGate = timeoutGate,
            actionCooldownGate = actionCooldownGate,
            dynamicValueGate = dynamicValueGate,
            executeRule = { rule, _ ->
                executedRuleIds += rule.id
                true
            },
            logClickDecision = { _, _, _ -> },
            onLog = { }
        )

        nowMs += 3_000L
        val third = handleRecognizedTextWithRules(
            rules = rules,
            packageName = "",
            text = "继续领奖励",
            matcher = matcher,
            timeoutGate = timeoutGate,
            actionCooldownGate = actionCooldownGate,
            dynamicValueGate = dynamicValueGate,
            executeRule = { rule, _ ->
                executedRuleIds += rule.id
                true
            },
            logClickDecision = { _, _, _ -> },
            onLog = { }
        )

        assertEquals("ad_next", first.status)
        assertEquals("skip:ad_next", second.status)
        assertEquals("ad_next", third.status)
        assertEquals(listOf("ad_next", "ad_next"), executedRuleIds)
    }

    @Test
    fun handleRecognizedTextWithRules_doesNotBlockDifferentRuleIdDuringCooldownWindow() {
        var nowMs = 1_000L
        val matcher = OcrRuleMatcher()
        val timeoutGate = OcrRuleTimeoutGate()
        val actionCooldownGate = OcrActionCooldownGate(nowProvider = { nowMs })
        val dynamicValueGate = OcrDynamicValueGate()
        val executedRuleIds = mutableListOf<String>()
        val rules = listOf(
            OcrActionRule(
                id = "ad_next",
                priority = 1,
                keywords = listOf("继续领奖励"),
                action = OcrRuleAction.Click(OcrClickTarget(0.5f, 0.5f))
            ),
            OcrActionRule(
                id = "ad_done",
                priority = 0,
                keywords = listOf("坚持退出"),
                action = OcrRuleAction.Back
            )
        )

        handleRecognizedTextWithRules(
            rules = rules,
            packageName = "",
            text = "继续领奖励",
            matcher = matcher,
            timeoutGate = timeoutGate,
            actionCooldownGate = actionCooldownGate,
            dynamicValueGate = dynamicValueGate,
            executeRule = { rule, _ ->
                executedRuleIds += rule.id
                true
            },
            logClickDecision = { _, _, _ -> },
            onLog = { }
        )

        nowMs += 2_000L
        val second = handleRecognizedTextWithRules(
            rules = rules,
            packageName = "",
            text = "坚持退出",
            matcher = matcher,
            timeoutGate = timeoutGate,
            actionCooldownGate = actionCooldownGate,
            dynamicValueGate = dynamicValueGate,
            executeRule = { rule, _ ->
                executedRuleIds += rule.id
                true
            },
            logClickDecision = { _, _, _ -> },
            onLog = { }
        )

        assertEquals("ad_done", second.status)
        assertEquals(listOf("ad_next", "ad_done"), executedRuleIds)
    }
}
