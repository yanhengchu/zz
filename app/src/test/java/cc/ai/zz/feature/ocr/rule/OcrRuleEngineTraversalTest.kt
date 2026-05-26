package cc.ai.zz.feature.ocr.rule

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrRuleEngineTraversalTest {
    @Test
    fun selectRuleInOrder_usesNextRuleWhenRuleSkips() {
        val items = listOf(
            RuleCase("first", OcrRuleTraversalResult.Skip("first")),
            RuleCase("second", OcrRuleTraversalResult.Execute("second")),
            RuleCase("third", OcrRuleTraversalResult.Execute("third"))
        )

        val selected = selectRuleInOrder(
            items = items,
            evaluate = { it.result }
        )

        assertEquals(OcrRuleTraversalResult.Execute("second"), selected)
    }

    @Test
    fun selectRuleInOrder_returnsLastSkipWhenNoRuleExecutes() {
        val items = listOf(
            RuleCase("first", OcrRuleTraversalResult.Skip("first")),
            RuleCase("second", OcrRuleTraversalResult.NoMatch),
            RuleCase("third", OcrRuleTraversalResult.Skip("third"))
        )

        val selected = selectRuleInOrder(
            items = items,
            evaluate = { it.result }
        )

        assertEquals(OcrRuleTraversalResult.Skip("third"), selected)
    }

    @Test
    fun selectRuleInOrder_fallsThroughWhenRuleHasNoMatch() {
        val items = listOf(
            RuleCase("first", OcrRuleTraversalResult.NoMatch),
            RuleCase("second", OcrRuleTraversalResult.Execute("second"))
        )

        val selected = selectRuleInOrder(
            items = items,
            evaluate = { it.result }
        )

        assertEquals(OcrRuleTraversalResult.Execute("second"), selected)
    }

    private data class RuleCase(
        val id: String,
        val result: OcrRuleTraversalResult
    )
}
