package cc.ai.zz.feature.ocr.rule

data class OcrActionRule(
    val id: String,
    val priority: Int,
    val packages: List<String> = emptyList(),
    val keywords: List<String>,
    val action: OcrRuleAction,
    val valuePolicy: OcrValuePolicy? = null,
    val timeout: OcrRuleTimeout? = null,
    val log: Boolean = false
)

data class OcrRuleTimeout(
    val awaitRuleId: String,
    val timeoutSeconds: Int
)

sealed interface OcrValuePolicy {
    data object Changed : OcrValuePolicy

    data object Unchanged : OcrValuePolicy

    data class NumericThreshold(
        val operator: NumericCompareOperator,
        val threshold: Int
    ) : OcrValuePolicy
}

enum class NumericCompareOperator {
    LT,
    LTE,
    GT,
    GTE,
    EQ;

    fun matches(currentValue: Int, threshold: Int): Boolean {
        return when (this) {
            LT -> currentValue < threshold
            LTE -> currentValue <= threshold
            GT -> currentValue > threshold
            GTE -> currentValue >= threshold
            EQ -> currentValue == threshold
        }
    }
}

data class OcrClickTarget(
    val xRatio: Float,
    val yRatio: Float
)

sealed interface OcrRuleAction {
    data object Wait : OcrRuleAction

    data object Back : OcrRuleAction

    data object Swipe : OcrRuleAction

    data class Click(
        val target: OcrClickTarget,
        val elseTarget: OcrClickTarget? = null
    ) : OcrRuleAction
}

data class OcrRuleHandleResult(
    val status: String,
    val shouldLogText: Boolean = false
)
