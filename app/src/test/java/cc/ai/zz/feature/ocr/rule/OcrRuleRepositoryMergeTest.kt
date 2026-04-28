package cc.ai.zz.feature.ocr.rule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrRuleRepositoryMergeTest {
    @Test
    fun mergeRules_overridesBundledRuleWhenExternalUsesSameId() {
        val bundledRule = createRule(id = "ad_back", priority = 10)
        val externalRule = createRule(id = "ad_back", priority = 99)

        val mergedRules = OcrRuleRepository.mergeRules(
            bundledRules = listOf(bundledRule),
            externalRules = listOf(externalRule)
        )

        assertEquals(1, mergedRules.size)
        assertEquals(99, mergedRules.single().priority)
    }

    @Test
    fun mergeRules_appendsExternalRuleWhenIdIsNew() {
        val bundledRule = createRule(id = "ad_back", priority = 10)
        val externalRule = createRule(id = "exp_rule", priority = 20)

        val mergedRules = OcrRuleRepository.mergeRules(
            bundledRules = listOf(bundledRule),
            externalRules = listOf(externalRule)
        )

        assertEquals(listOf("exp_rule", "ad_back"), mergedRules.map { it.id })
    }

    @Test
    fun mergeRules_externalCsvRuleOverridesBundledRuleWithSameId() {
        val bundledRule = createRule(id = "ad_back", priority = 10)
        val csvRule = createRule(id = "ad_back", priority = 30)

        val mergedRules = OcrRuleRepository.mergeRules(
            bundledRules = listOf(bundledRule),
            externalRules = listOf(csvRule)
        )

        assertEquals(30, mergedRules.single().priority)
    }

    @Test
    fun mergeRules_externalSwipeRuleOverridesBundledRuleAndKeepsAliasKeywords() {
        val bundledRule = createRule(id = "video_swipe", priority = 10)
        val externalRule = OcrActionRule(
            id = "video_swipe",
            priority = 20,
            keywords = listOf("首页", "倒计时mm:ss/倒计吋mm:ss"),
            action = OcrRuleAction.Swipe,
            valuePolicy = OcrValuePolicy.Changed
        )

        val mergedRules = OcrRuleRepository.mergeRules(
            bundledRules = listOf(bundledRule),
            externalRules = listOf(externalRule)
        )

        assertEquals(1, mergedRules.size)
        assertEquals(20, mergedRules.single().priority)
        assertEquals(listOf("首页", "倒计时mm:ss/倒计吋mm:ss"), mergedRules.single().keywords)
        assertEquals(OcrRuleAction.Swipe, mergedRules.single().action)
        assertEquals(OcrValuePolicy.Changed, mergedRules.single().valuePolicy)
    }

    @Test
    fun mergeRules_resolutionOverridesBundledAndExternalOverridesResolution() {
        val bundledRule = createRule(id = "ad_next", priority = 0)
        val resolutionRule = createRule(id = "ad_next", priority = 5)
        val externalRule = createRule(id = "ad_next", priority = 9)

        val bundledMerged = OcrRuleRepository.mergeRules(
            bundledRules = listOf(bundledRule),
            externalRules = listOf(resolutionRule)
        )
        val finalMerged = OcrRuleRepository.mergeRules(
            bundledRules = bundledMerged,
            externalRules = listOf(externalRule)
        )

        assertEquals(1, finalMerged.size)
        assertEquals(9, finalMerged.single().priority)
    }

    @Test
    fun mergeRulePatches_overridesOnlySpecifiedFields() {
        val baseRule = OcrActionRule(
            id = "ad_next",
            priority = 2,
            packages = listOf("cc.ai.zz"),
            keywords = listOf("继续领奖励", "坚持退出"),
            action = OcrRuleAction.Click(
                target = OcrClickTarget(0.50f, 0.57f),
                elseTarget = OcrClickTarget(0.50f, 0.63f)
            ),
            valuePolicy = OcrValuePolicy.NumericThreshold(NumericCompareOperator.GT, 300),
            log = true
        )

        val patches = OcrRuleRepository.parseCsvRulePatches(
            """
id,priority,log,timeout,pkg,keywords,action_type,value_policy,action_target,else_target
ad_next,,,,,,,,0.48:0.55,0.48:0.61
            """.trimIndent()
        )

        val mergedRules = OcrRuleRepository.mergeRulePatches(listOf(baseRule), patches)
        val mergedRule = mergedRules.single()

        assertEquals(2, mergedRule.priority)
        assertEquals(listOf("cc.ai.zz"), mergedRule.packages)
        assertEquals(listOf("继续领奖励", "坚持退出"), mergedRule.keywords)
        assertEquals(OcrValuePolicy.NumericThreshold(NumericCompareOperator.GT, 300), mergedRule.valuePolicy)
        assertEquals(true, mergedRule.log)
        val clickAction = mergedRule.action as OcrRuleAction.Click
        assertEquals(0.48f, clickAction.target.xRatio)
        assertEquals(0.55f, clickAction.target.yRatio)
        assertEquals(0.48f, clickAction.elseTarget?.xRatio)
        assertEquals(0.61f, clickAction.elseTarget?.yRatio)
    }

    @Test
    fun resolveClosestResolutionRulesAssetPath_prefersExactMatch() {
        val path = OcrRuleRepository.resolveClosestResolutionRulesAssetPath(
            width = 1172,
            height = 2748,
            assetPaths = listOf(
                "ocr_rules_default.csv",
                "ocr_rules_1080x2313.csv",
                "ocr_rules_1172x2748.csv"
            )
        )

        assertEquals("ocr_rules_1172x2748.csv", path)
    }

    @Test
    fun resolveClosestResolutionRulesAssetPath_usesNearestRatioWhenExactMissing() {
        val path = OcrRuleRepository.resolveClosestResolutionRulesAssetPath(
            width = 1200,
            height = 2800,
            assetPaths = listOf(
                "ocr_rules_default.csv",
                "ocr_rules_1080x2313.csv",
                "ocr_rules_1172x2748.csv"
            )
        )

        assertEquals("ocr_rules_1172x2748.csv", path)
    }

    @Test
    fun resolveClosestResolutionRulesAssetPath_ignoresDefaultWhenItIsOnlyCandidateForFallback() {
        val path = OcrRuleRepository.resolveClosestResolutionRulesAssetPath(
            width = 1200,
            height = 2800,
            assetPaths = listOf("ocr_rules_default.csv")
        )

        assertNull(path)
    }

    private fun createRule(id: String, priority: Int): OcrActionRule {
        return OcrActionRule(
            id = id,
            priority = priority,
            keywords = listOf("广告", "领取成功"),
            action = OcrRuleAction.Wait
        )
    }
}
