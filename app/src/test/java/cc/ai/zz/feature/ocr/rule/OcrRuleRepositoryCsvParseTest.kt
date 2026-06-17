package cc.ai.zz.feature.ocr.rule

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrRuleRepositoryCsvParseTest {
    @Test
    fun parseCsvRules_parsesMinimalAndOptionalFields() {
        val rules = OcrRuleRepository.parseCsvRules(
            """
id,log,timeout,pkg,keywords,exclude_keywords,action_type,value_policy,action_target,else_target
exp_back,true,,,"广告|领取成功",,BACK,,,
exp_swipe,0,,cc.ai.zz|com.demo.app,"首页|倒计时mm:ss/倒计吋mm:ss",,SWIPE,CHANGED,,
exp_cont,1,act_back:40,,"广告|领取成功|继续领奖励",,CLICK,LT:300,0.48:0.56,0.82:0.56
exp_exclude,0,,com.demo.app,"看视频再得num金币","首页|直播间",CLICK,,0.50:0.60,
            """.trimIndent()
        )

        assertEquals(listOf("exp_back", "exp_swipe", "exp_cont", "exp_exclude"), rules.map { it.id })

        val backRule = rules.first()
        assertEquals(listOf("广告", "领取成功"), backRule.keywords)
        assertEquals(true, backRule.log)

        val swipeRule = rules[1]
        assertEquals("exp_swipe", swipeRule.id)
        assertEquals(listOf("cc.ai.zz", "com.demo.app"), swipeRule.packages)
        assertEquals(OcrRuleAction.Swipe, swipeRule.action)
        assertEquals(OcrValuePolicy.Changed, swipeRule.valuePolicy)
        assertEquals(false, swipeRule.log)

        val clickRule = rules[2]
        assertEquals(true, clickRule.log)
        val clickAction = clickRule.action as OcrRuleAction.Click
        assertEquals(0.48f, clickAction.target.xRatio)
        assertEquals(0.56f, clickAction.target.yRatio)
        assertEquals(0.82f, clickAction.elseTarget?.xRatio)
        assertEquals(0.56f, clickAction.elseTarget?.yRatio)
        assertEquals(
            OcrValuePolicy.NumericThreshold(NumericCompareOperator.LT, 300),
            clickRule.valuePolicy
        )
        assertEquals(
            OcrRuleTimeout(awaitRuleId = "act_back", timeoutSeconds = 40),
            clickRule.timeout
        )

        val excludeRule = rules.last()
        assertEquals(listOf("看视频再得num金币"), excludeRule.keywords)
        assertEquals(listOf("首页", "直播间"), excludeRule.excludeKeywords)
    }

    @Test
    fun parseCsvRules_parsesRuntimeNumericThreshold() {
        val rules = OcrRuleRepository.parseCsvRules(
            """
id,log,timeout,pkg,keywords,exclude_keywords,action_type,value_policy,action_target,else_target
live_ad_next,0,,,"num金币|继续领奖励",,CLICK,GT:AD_NEXT_THRESHOLD,0.50:0.56,0.82:0.56
            """.trimIndent()
        )

        assertEquals(
            OcrValuePolicy.RuntimeNumericThreshold(NumericCompareOperator.GT, "AD_NEXT_THRESHOLD"),
            rules.single().valuePolicy
        )
    }

    @Test
    fun parseCsvRules_ignoresDeprecatedPriorityColumnAndKeepsCsvOrder() {
        val rules = OcrRuleRepository.parseCsvRules(
            """
id,priority,log,timeout,pkg,keywords,exclude_keywords,action_type,value_policy,action_target,else_target
first,0,0,,,"第一条",,WAIT,,,
second,99,0,,,"第二条",,WAIT,,,
            """.trimIndent()
        )

        assertEquals(listOf("first", "second"), rules.map { it.id })
    }

    @Test
    fun parseCsvRules_usesCenterTargetWhenClickTargetIsMissing() {
        val rules = OcrRuleRepository.parseCsvRules(
            """
id,log,timeout,pkg,keywords,exclude_keywords,action_type,value_policy,action_target,else_target
click_missing_target,0,,,"点击",,CLICK,,,
            """.trimIndent()
        )

        val action = rules.single().action as OcrRuleAction.Click
        assertEquals(0.5f, action.target.xRatio)
        assertEquals(0.5f, action.target.yRatio)
    }

    @Test
    fun parseCsvRules_parsesClickSequenceTargets() {
        val rules = OcrRuleRepository.parseCsvRules(
            """
id,log,timeout,pkg,keywords,exclude_keywords,action_type,value_policy,action_target,else_target
seq_buttons,0,,,"任务页",,CLICK_SEQUENCE,,0.10:0.20|0.30:0.40|0.50:0.60,
            """.trimIndent()
        )

        val action = rules.single().action as OcrRuleAction.ClickSequence
        assertEquals(3, action.targets.size)
        assertEquals(0.10f, action.targets[0].xRatio)
        assertEquals(0.20f, action.targets[0].yRatio)
        assertEquals(0.50f, action.targets[2].xRatio)
        assertEquals(0.60f, action.targets[2].yRatio)
    }

    @Test
    fun parseCsvRules_usesCenterTargetWhenClickSequenceTargetIsMissing() {
        val rules = OcrRuleRepository.parseCsvRules(
            """
id,log,timeout,pkg,keywords,exclude_keywords,action_type,value_policy,action_target,else_target
seq_missing_target,0,,,"任务页",,CLICK_SEQUENCE,,,
            """.trimIndent()
        )

        val action = rules.single().action as OcrRuleAction.ClickSequence
        assertEquals(1, action.targets.size)
        assertEquals(0.5f, action.targets.single().xRatio)
        assertEquals(0.5f, action.targets.single().yRatio)
    }

    @Test
    fun parseCsvConfusions_parsesCanonicalCharacterMappings() {
        val confusions = OcrRuleRepository.parseCsvConfusions(
            """
canonical,variants
已,"已|己|乙"
持,"持|待"
时,"时|吋|盯"
领,"领|顿|额"
            """.trimIndent()
        )

        assertEquals('已', confusions['已'])
        assertEquals('已', confusions['己'])
        assertEquals('已', confusions['乙'])
        assertEquals('持', confusions['待'])
        assertEquals('时', confusions['吋'])
        assertEquals('时', confusions['盯'])
        assertEquals('领', confusions['顿'])
        assertEquals('领', confusions['额'])
    }

    @Test
    fun parseCsvCleanConfig_parsesLineFiltersAndRemovableChars() {
        val config = OcrRuleRepository.parseCsvCleanConfig(
            """
type,value
remove_ascii_punctuation,1
remove_ascii_letters,1
remove_chars,"，。、；！？·（）"
drop_line_prefix,"ocr:"
drop_line_contains,"调试文案"
drop_line_exact,"完整过滤"
            """.trimIndent()
        )

        assertEquals(true, config.removeAsciiPunctuation)
        assertEquals(true, config.removeAsciiLetters)
        assertEquals(true, config.removableChars.contains('·'))
        assertEquals(listOf("ocr:"), config.dropLinePrefixes)
        assertEquals(listOf("调试文案"), config.dropLineContains)
        assertEquals(listOf("完整过滤"), config.dropLineExact)
    }
}
