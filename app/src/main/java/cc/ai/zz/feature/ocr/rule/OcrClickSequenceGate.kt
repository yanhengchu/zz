package cc.ai.zz.feature.ocr.rule

class OcrClickSequenceGate {
    data class IndexedTarget(
        val index: Int,
        val target: OcrClickTarget
    )

    private val nextIndexByRuleId = mutableMapOf<String, Int>()

    fun reset() {
        nextIndexByRuleId.clear()
    }

    fun resolveTarget(rule: OcrActionRule): IndexedTarget? {
        val action = rule.action as? OcrRuleAction.ClickSequence ?: return null
        if (action.targets.isEmpty()) return null
        val index = nextIndexByRuleId[rule.id].orZero().floorMod(action.targets.size)
        return IndexedTarget(index = index, target = action.targets[index])
    }

    fun onRuleExecuted(rule: OcrActionRule, executed: Boolean) {
        if (!executed) return
        val action = rule.action as? OcrRuleAction.ClickSequence ?: return
        if (action.targets.isEmpty()) return
        val currentIndex = nextIndexByRuleId[rule.id].orZero().floorMod(action.targets.size)
        nextIndexByRuleId[rule.id] = (currentIndex + 1).floorMod(action.targets.size)
    }

    private fun Int?.orZero(): Int = this ?: 0

    private fun Int.floorMod(modulus: Int): Int {
        return ((this % modulus) + modulus) % modulus
    }
}
