package cc.ai.zz.feature.ocr.rule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrRuleRepositoryMergeTest {
    @Test
    fun mergeRules_overridesBundledRuleWhenExternalUsesSameId() {
        val bundledRule = createRule(id = "ad_back", keywords = listOf("旧广告"))
        val externalRule = createRule(id = "ad_back", keywords = listOf("新广告"))

        val mergedRules = OcrRuleRepository.mergeRules(
            bundledRules = listOf(bundledRule),
            externalRules = listOf(externalRule)
        )

        assertEquals(1, mergedRules.size)
        assertEquals(externalRule, mergedRules.single())
    }

    @Test
    fun mergeRules_appendsExternalRuleWhenIdIsNew() {
        val bundledRule = createRule(id = "ad_back")
        val externalRule = createRule(id = "exp_rule")

        val mergedRules = OcrRuleRepository.mergeRules(
            bundledRules = listOf(bundledRule),
            externalRules = listOf(externalRule)
        )

        assertEquals(listOf("ad_back", "exp_rule"), mergedRules.map { it.id })
    }

    @Test
    fun mergeRules_externalCsvRuleOverridesBundledRuleWithSameId() {
        val bundledRule = createRule(id = "ad_back", keywords = listOf("旧广告"))
        val csvRule = createRule(id = "ad_back", keywords = listOf("新广告"))

        val mergedRules = OcrRuleRepository.mergeRules(
            bundledRules = listOf(bundledRule),
            externalRules = listOf(csvRule)
        )

        assertEquals(csvRule, mergedRules.single())
    }

    @Test
    fun mergeRules_externalSwipeRuleOverridesBundledRuleAndKeepsAliasKeywords() {
        val bundledRule = createRule(id = "video_swipe")
        val externalRule = OcrActionRule(
            id = "video_swipe",
            keywords = listOf("首页", "倒计时mm:ss/倒计吋mm:ss"),
            action = OcrRuleAction.Swipe,
            valuePolicy = OcrValuePolicy.Changed
        )

        val mergedRules = OcrRuleRepository.mergeRules(
            bundledRules = listOf(bundledRule),
            externalRules = listOf(externalRule)
        )

        assertEquals(1, mergedRules.size)
        assertEquals(listOf("首页", "倒计时mm:ss/倒计吋mm:ss"), mergedRules.single().keywords)
        assertEquals(OcrRuleAction.Swipe, mergedRules.single().action)
        assertEquals(OcrValuePolicy.Changed, mergedRules.single().valuePolicy)
    }

    @Test
    fun mergeRules_resolutionOverridesBundledAndExternalOverridesResolution() {
        val bundledRule = createRule(id = "ad_next", keywords = listOf("默认"))
        val resolutionRule = createRule(id = "ad_next", keywords = listOf("分辨率覆盖"))
        val externalRule = createRule(id = "ad_next", keywords = listOf("外部覆盖"))

        val bundledMerged = OcrRuleRepository.mergeRules(
            bundledRules = listOf(bundledRule),
            externalRules = listOf(resolutionRule)
        )
        val finalMerged = OcrRuleRepository.mergeRules(
            bundledRules = bundledMerged,
            externalRules = listOf(externalRule)
        )

        assertEquals(1, finalMerged.size)
        assertEquals(externalRule, finalMerged.single())
    }

    @Test
    fun mergeRulePatches_overridesOnlySpecifiedFields() {
        val baseRule = OcrActionRule(
            id = "ad_next",
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
id,log,timeout,pkg,keywords,exclude_keywords,action_type,value_policy,action_target,else_target
ad_next,,,,,,,,0.48:0.55,0.48:0.61
            """.trimIndent()
        )

        val mergedRules = OcrRuleRepository.mergeRulePatches(listOf(baseRule), patches)
        val mergedRule = mergedRules.single()

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

    private fun createRule(
        id: String,
        keywords: List<String> = listOf("广告", "领取成功")
    ): OcrActionRule {
        return OcrActionRule(
            id = id,
            keywords = keywords,
            action = OcrRuleAction.Wait
        )
    }
}
