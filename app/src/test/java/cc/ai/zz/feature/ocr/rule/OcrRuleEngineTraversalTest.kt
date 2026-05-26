package cc.ai.zz.feature.ocr.rule

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrRuleEngineTraversalTest {
    @Test
    fun selectRuleWithinPriorityGroup_usesNextRuleWhenSamePrioritySkips() {
        val items = listOf(
            RuleCase("first", 10, OcrRuleTraversalResult.Skip("first")),
            RuleCase("second", 10, OcrRuleTraversalResult.Execute("second")),
            RuleCase("third", 0, OcrRuleTraversalResult.Execute("third"))
        )

        val selected = selectRuleWithinPriorityGroup(
            items = items,
            priorityOf = { it.priority },
            evaluate = { it.result }
        )

        assertEquals(OcrRuleTraversalResult.Execute("second"), selected)
    }

    @Test
    fun selectRuleWithinPriorityGroup_doesNotFallThroughToLowerPriorityAfterSkip() {
        val items = listOf(
            RuleCase("first", 10, OcrRuleTraversalResult.Skip("first")),
            RuleCase("second", 0, OcrRuleTraversalResult.Execute("second"))
        )

        val selected = selectRuleWithinPriorityGroup(
            items = items,
            priorityOf = { it.priority },
            evaluate = { it.result }
        )

        assertEquals(OcrRuleTraversalResult.Skip("first"), selected)
    }

    @Test
    fun selectRuleWithinPriorityGroup_fallsThroughWhenHigherPriorityHasNoMatch() {
        val items = listOf(
            RuleCase("first", 10, OcrRuleTraversalResult.NoMatch),
            RuleCase("second", 0, OcrRuleTraversalResult.Execute("second"))
        )

        val selected = selectRuleWithinPriorityGroup(
            items = items,
            priorityOf = { it.priority },
            evaluate = { it.result }
        )

        assertEquals(OcrRuleTraversalResult.Execute("second"), selected)
    }

    private data class RuleCase(
        val id: String,
        val priority: Int,
        val result: OcrRuleTraversalResult
    )
}
