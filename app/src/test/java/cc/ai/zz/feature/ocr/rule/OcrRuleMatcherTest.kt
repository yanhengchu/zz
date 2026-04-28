package cc.ai.zz.feature.ocr.rule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrRuleMatcherTest {
    private val matcher = OcrRuleMatcher()
    private val cleaner = OcrTextCleaner(
        OcrTextCleanConfig(
            removeAsciiPunctuation = true,
            removableChars = setOf('，', '。', '、', '；', '！', '？', '·', '（', '）'),
            dropLinePrefixes = listOf("ocr:"),
            dropLineContains = listOf("调试浮窗")
        )
    )
    private val matcherWithConfusions = OcrRuleMatcher(
        mapOf(
            '己' to '已',
            '乙' to '已',
            '待' to '持',
            '吋' to '时',
            '盯' to '时',
            '顿' to '领',
            '额' to '领'
        )
    )
    private val matcherWithAsciiLettersRemoved = OcrRuleMatcher(
        textCleaner = OcrTextCleaner(
            OcrTextCleanConfig(
                removeAsciiPunctuation = true,
                removeAsciiLetters = true,
                removableChars = setOf('，', '。', '、', '；', '！', '？', '·', '（', '）')
            )
        )
    )

    @Test
    fun findFirstMatch_returnsHighestPriorityMatchedRule() {
        val lowPriorityRule = createRule(
            id = "low",
            priority = 10,
            keywords = listOf("广告", "领取成功")
        )
        val highPriorityRule = createRule(
            id = "high",
            priority = 20,
            keywords = listOf("广告", "领取成功", "继续领奖励")
        )

        val matchedRule = matcher.findFirstMatch(
            rules = listOf(highPriorityRule, lowPriorityRule),
            text = "广告 领取成功 继续领奖励"
        )

        assertEquals("high", matchedRule?.rule?.id)
    }

    @Test
    fun findFirstMatch_returnsNullWhenKeywordIsMissing() {
        val rule = createRule(
            id = "back",
            priority = 10,
            keywords = listOf("广告", "领取成功", "继续领奖励")
        )

        val matchedRule = matcher.findFirstMatch(
            rules = listOf(rule),
            text = "广告 领取成功"
        )

        assertNull(matchedRule)
    }

    @Test
    fun findFirstMatch_allowsSameRuleToMatchAgainOnNextRound() {
        val rule = createRule(
            id = "back",
            priority = 10,
            keywords = listOf("广告", "领取成功")
        )

        val firstMatch = matcher.findFirstMatch(
            rules = listOf(rule),
            text = "广告 领取成功"
        )

        val secondMatch = matcher.findFirstMatch(
            rules = listOf(rule),
            text = "广告 领取成功"
        )

        assertEquals("back", firstMatch?.rule?.id)
        assertEquals("back", secondMatch?.rule?.id)
    }

    @Test
    fun findFirstMatch_ignoresSpacesAndPunctuation() {
        val rule = createRule(
            id = "video_swipe",
            priority = 10,
            keywords = listOf("首页", "去赚钱")
        )

        val matchedRule = matcher.findFirstMatch(
            rules = listOf(rule),
            text = "首页 朋友，去赚钱 我"
        )

        assertEquals("video_swipe", matchedRule?.rule?.id)
    }

    @Test
    fun findFirstMatch_supportsKeywordAliasesInSingleSlot() {
        val rule = createRule(
            id = "video_swipe",
            priority = 10,
            keywords = listOf("首页", "去赚钱", "倒计时/倒计吋")
        )

        val matchedRule = matcher.findFirstMatch(
            rules = listOf(rule),
            text = "首页 朋友，去赚钱 我 倒计吋"
        )

        assertEquals("video_swipe", matchedRule?.rule?.id)
    }

    @Test
    fun findFirstMatch_supportsConfusionDictionaryForSingleCharacters() {
        val rule = createRule(
            id = "ad_done",
            priority = 10,
            keywords = listOf("已成功领取奖励", "坚持退出", "倒计时mm:ss")
        )

        val matchedRule = matcherWithConfusions.findFirstMatch(
            rules = listOf(rule),
            text = "己成功领取奖励 坚待退出 倒计吋89:30"
        )

        assertEquals("ad_done", matchedRule?.rule?.id)
        assertEquals("89:30", matchedRule?.dynamicValue)
    }

    @Test
    fun findFirstMatch_supportsRewardConfusionsWithoutExtraAliasPhrases() {
        val rule = createRule(
            id = "ad_wait",
            priority = 10,
            keywords = listOf("后可领奖励")
        )

        val firstMatch = matcherWithConfusions.findFirstMatch(
            rules = listOf(rule),
            text = "后可顿奖励"
        )
        val secondMatch = matcherWithConfusions.findFirstMatch(
            rules = listOf(rule),
            text = "后可额奖励"
        )

        assertEquals("ad_wait", firstMatch?.rule?.id)
        assertEquals("ad_wait", secondMatch?.rule?.id)
    }

    @Test
    fun findFirstMatch_supportsEscapedSlashInsideKeyword() {
        val rule = createRule(
            id = "checkin",
            priority = 10,
            keywords = listOf("今日打卡任务 4\\/5", "广告完成打卡")
        )

        val matchedRule = matcher.findFirstMatch(
            rules = listOf(rule),
            text = "完成365天打卡任务白拿好礼 今日打卡任务 4/5 广告完成打卡"
        )

        assertEquals("checkin", matchedRule?.rule?.id)
    }

    @Test
    fun findFirstMatch_treatsSpaceInsideEscapedSlashKeywordAsEquivalent() {
        val ruleWithSpace = createRule(
            id = "with_space",
            priority = 10,
            keywords = listOf("今日打卡任务 4\\/5")
        )
        val ruleWithoutSpace = createRule(
            id = "without_space",
            priority = 10,
            keywords = listOf("今日打卡任务4\\/5")
        )

        val textWithSpace = "今日打卡任务 4/5"
        val textWithoutSpace = "今日打卡任务4/5"

        assertEquals(
            "with_space",
            matcher.findFirstMatch(listOf(ruleWithSpace), textWithSpace)?.rule?.id
        )
        assertEquals(
            "with_space",
            matcher.findFirstMatch(listOf(ruleWithSpace), textWithoutSpace)?.rule?.id
        )
        assertEquals(
            "without_space",
            matcher.findFirstMatch(listOf(ruleWithoutSpace), textWithSpace)?.rule?.id
        )
        assertEquals(
            "without_space",
            matcher.findFirstMatch(listOf(ruleWithoutSpace), textWithoutSpace)?.rule?.id
        )
    }

    @Test
    fun findFirstMatch_supportsTimePlaceholderAliases() {
        val rule = createRule(
            id = "video_swipe",
            priority = 10,
            keywords = listOf("首页", "倒计时mm:ss/倒计吋mm:ss")
        )

        val matchedRule = matcher.findFirstMatch(
            rules = listOf(rule),
            text = "首页 倒计吋89:30"
        )

        assertEquals("video_swipe", matchedRule?.rule?.id)
    }

    @Test
    fun findFirstMatch_keepsTimePlaceholderWhenAsciiLettersAreRemoved() {
        val rule = createRule(
            id = "video_swipe",
            priority = 10,
            keywords = listOf("倒计时mm:ss")
        )

        val matchedRule = matcherWithAsciiLettersRemoved.findFirstMatch(
            rules = listOf(rule),
            text = "倒计时89:30"
        )

        assertEquals("video_swipe", matchedRule?.rule?.id)
        assertEquals("89:30", matchedRule?.dynamicValue)
    }

    @Test
    fun findFirstMatch_supportsNumberPlaceholderAliases() {
        val rule = createRule(
            id = "ad_bonus",
            priority = 10,
            keywords = listOf("看视频再得num金币/看广告视频再得num金币")
        )

        val firstMatch = matcher.findFirstMatch(
            rules = listOf(rule),
            text = "看视频再得568金币"
        )
        val secondMatch = matcher.findFirstMatch(
            rules = listOf(rule),
            text = "看广告视频再得1200金币"
        )
        val thirdMatch = matcher.findFirstMatch(
            rules = listOf(rule),
            text = "看视频再得98·金币"
        )

        assertEquals("ad_bonus", firstMatch?.rule?.id)
        assertEquals("ad_bonus", secondMatch?.rule?.id)
        assertEquals("ad_bonus", thirdMatch?.rule?.id)
        assertEquals("568", firstMatch?.dynamicValue)
        assertEquals("1200", secondMatch?.dynamicValue)
        assertEquals("98", thirdMatch?.dynamicValue)
    }

    @Test
    fun findFirstMatch_keepsNumberPlaceholderWhenAsciiLettersAreRemoved() {
        val rule = createRule(
            id = "ad_next",
            priority = 10,
            keywords = listOf("num再看一个视频继续领奖励", "继续领奖励", "坚持退出")
        )

        val matchedRule = matcherWithAsciiLettersRemoved.findFirstMatch(
            rules = listOf(rule),
            text = "198 再看一个视频继续领奖励 继续领奖励 坚持退出"
        )

        assertEquals("ad_next", matchedRule?.rule?.id)
        assertEquals("198", matchedRule?.dynamicValue)
    }

    @Test
    fun findFirstMatch_extractsTimerDynamicValue() {
        val rule = OcrActionRule(
            id = "video_swipe",
            priority = 10,
            keywords = listOf("首页", "倒计时mm:ss/倒计吋mm:ss"),
            action = OcrRuleAction.Swipe,
            valuePolicy = OcrValuePolicy.Changed
        )

        val matchedRule = matcher.findFirstMatch(
            rules = listOf(rule),
            text = "首页 倒计吋120:30"
        )

        assertEquals("120:30", matchedRule?.dynamicValue)
    }

    @Test
    fun textCleaner_filtersConfiguredLinesAndCharacters() {
        val text = cleaner.normalizeRecognizedText(
            """
ocr: none
再看一个视频继续领奖励
98·金币
这是调试浮窗文案
            """.trimIndent()
        )

        assertEquals("再看一个视频继续领奖励\n98·金币", text)
    }

    @Test
    fun findFirstMatch_usesConfiguredCleanerForCustomCharacters() {
        val matcherWithCleaner = OcrRuleMatcher(
            textCleaner = cleaner
        )
        val rule = createRule(
            id = "ad_bonus",
            priority = 10,
            keywords = listOf("num金币")
        )

        val matchedRule = matcherWithCleaner.findFirstMatch(
            rules = listOf(rule),
            text = "98·金币"
        )

        assertEquals("ad_bonus", matchedRule?.rule?.id)
        assertEquals("98", matchedRule?.dynamicValue)
    }

    @Test
    fun findFirstMatch_honorsConfiguredPackages() {
        val globalRule = createRule(
            id = "global",
            priority = 10,
            keywords = listOf("广告")
        )
        val scopedRule = OcrActionRule(
            id = "scoped",
            priority = 20,
            packages = listOf("com.demo.target", "cc.ai.zz"),
            keywords = listOf("广告"),
            action = OcrRuleAction.Wait
        )

        val matchedInTarget = matcher.findFirstMatch(
            rules = listOf(scopedRule, globalRule),
            text = "广告",
            packageName = "com.demo.target"
        )
        val matchedInOther = matcher.findFirstMatch(
            rules = listOf(scopedRule, globalRule),
            text = "广告",
            packageName = "com.demo.other"
        )

        assertEquals("scoped", matchedInTarget?.rule?.id)
        assertEquals("global", matchedInOther?.rule?.id)
    }

    @Test
    fun findFirstMatch_supportsAllKeywordAsWildcard() {
        val wildcardRule = OcrActionRule(
            id = "wildcard",
            priority = 20,
            keywords = listOf("ALL"),
            action = OcrRuleAction.Back
        )

        val matched = matcher.findFirstMatch(
            rules = listOf(wildcardRule),
            text = "领取成功"
        )

        assertEquals("wildcard", matched?.rule?.id)
    }

    @Test
    fun findFirstMatch_keepsAllKeywordWhenAsciiLettersAreRemoved() {
        val wildcardRule = OcrActionRule(
            id = "wildcard",
            priority = 20,
            keywords = listOf("ALL"),
            action = OcrRuleAction.Back
        )

        val matched = matcherWithAsciiLettersRemoved.findFirstMatch(
            rules = listOf(wildcardRule),
            text = "任意OCR结果"
        )

        assertEquals("wildcard", matched?.rule?.id)
    }

    @Test
    fun findFirstMatch_prefersHigherPriorityNonWildcardRuleOverAllKeyword() {
        val wildcardRule = OcrActionRule(
            id = "act_back",
            priority = 0,
            keywords = listOf("ALL"),
            action = OcrRuleAction.Back
        )
        val normalRule = createRule(
            id = "ad_done",
            priority = 10,
            keywords = listOf("领取成功")
        )

        val matched = matcher.findFirstMatch(
            rules = listOf(normalRule, wildcardRule),
            text = "领取成功"
        )

        assertEquals("ad_done", matched?.rule?.id)
    }

    @Test
    fun findFirstMatch_doesNotMatchTimeoutAllWithoutTriggeredTimeout() {
        val timeoutRule = OcrActionRule(
            id = "timeout_back",
            priority = 0,
            keywords = listOf("TIMEOUT_ALL"),
            action = OcrRuleAction.Back
        )

        val matched = matcher.findFirstMatch(
            rules = listOf(timeoutRule),
            text = "任意OCR结果"
        )

        assertEquals(null, matched)
    }

    @Test
    fun findFirstMatch_matchesTimeoutAllAfterTimeoutTriggered() {
        val timeoutRule = OcrActionRule(
            id = "timeout_back",
            priority = 0,
            keywords = listOf("TIMEOUT_ALL"),
            action = OcrRuleAction.Back
        )

        val matched = matcher.findFirstMatch(
            rules = listOf(timeoutRule),
            text = "任意OCR结果",
            timeoutTriggeredRuleIds = setOf("timeout_back")
        )

        assertEquals("timeout_back", matched?.rule?.id)
    }

    @Test
    fun findFirstMatch_keepsTimeoutAllKeywordWhenAsciiLettersAreRemoved() {
        val timeoutRule = OcrActionRule(
            id = "timeout_back",
            priority = 0,
            keywords = listOf("TIMEOUT_ALL"),
            action = OcrRuleAction.Back
        )

        val matched = matcherWithAsciiLettersRemoved.findFirstMatch(
            rules = listOf(timeoutRule),
            text = "任意OCR结果",
            timeoutTriggeredRuleIds = setOf("timeout_back")
        )

        assertEquals("timeout_back", matched?.rule?.id)
    }

    @Test
    fun textCleaner_removesAsciiLettersWhenConfigured() {
        val cleanerWithAsciiLettersRemoved = OcrTextCleaner(
            OcrTextCleanConfig(
                removeAsciiPunctuation = true,
                removeAsciiLetters = true,
                removableChars = setOf('·')
            )
        )
        val text = cleanerWithAsciiLettersRemoved.normalizeRecognizedText(
            """
ABC
98·金币
xyz任务
            """.trimIndent()
        )
        val normalized = cleanerWithAsciiLettersRemoved.normalizeForMatch(text)

        assertEquals("98金币任务", normalized)
    }

    private fun createRule(
        id: String,
        priority: Int,
        keywords: List<String>
    ): OcrActionRule {
        return OcrActionRule(
            id = id,
            priority = priority,
            keywords = keywords,
            action = OcrRuleAction.Wait,
        )
    }
}
