package cc.ai.zz.feature.ocr.rule

class OcrDynamicValueGate {
    private data class State(
        val observedValue: String,
        val pendingExecutionValue: String? = null
    )

    data class Decision(
        val shouldExecute: Boolean,
        val stateKey: String? = null,
        val currentValue: String? = null,
        val useElseTarget: Boolean = false
    )

    private val stateByRuleAndKey = mutableMapOf<String, State>()

    fun reset() {
        stateByRuleAndKey.clear()
    }

    fun evaluate(match: OcrRuleMatcher.OcrRuleMatch): Decision {
        val rule = match.rule
        val valuePolicy = rule.valuePolicy ?: return Decision(shouldExecute = true)
        val currentValue = match.dynamicValue

        if (valuePolicy is OcrValuePolicy.NumericThreshold) {
            val currentNumber = currentValue?.toIntOrNull() ?: return Decision(shouldExecute = true)
            val conditionMatched = valuePolicy.operator.matches(currentNumber, valuePolicy.threshold)
            val elseTargetAvailable = (rule.action as? OcrRuleAction.Click)?.elseTarget != null
            return when {
                conditionMatched -> Decision(shouldExecute = true, useElseTarget = false)
                elseTargetAvailable -> Decision(shouldExecute = true, useElseTarget = true)
                else -> Decision(shouldExecute = false)
            }
        }

        // If policy is configured but no time placeholder was matched,
        // fall back to ordinary rule execution instead of blocking the action.
        if (currentValue.isNullOrEmpty()) return Decision(shouldExecute = true)

        val stateKey = rule.id
        val state = stateByRuleAndKey[stateKey]
        val previousObservedValue = state?.observedValue
        val pendingExecutionValue = state?.pendingExecutionValue

        if (pendingExecutionValue == currentValue) {
            return Decision(
                shouldExecute = true,
                stateKey = stateKey,
                currentValue = currentValue
            )
        }

        return Decision(
            shouldExecute = when {
                previousObservedValue == null -> true
                valuePolicy == OcrValuePolicy.Changed -> currentValue != previousObservedValue
                else -> currentValue == previousObservedValue
            },
            stateKey = stateKey,
            currentValue = currentValue
        )
    }

    fun observe(decision: Decision) {
        val stateKey = decision.stateKey ?: return
        val currentValue = decision.currentValue ?: return
        val currentState = stateByRuleAndKey[stateKey]
        stateByRuleAndKey[stateKey] = State(
            observedValue = currentValue,
            pendingExecutionValue = currentState?.pendingExecutionValue?.takeIf { it == currentValue }
        )
    }

    fun onActionResult(decision: Decision, executed: Boolean) {
        val stateKey = decision.stateKey ?: return
        val currentValue = decision.currentValue ?: return
        val currentState = stateByRuleAndKey[stateKey] ?: State(observedValue = currentValue)
        stateByRuleAndKey[stateKey] = currentState.copy(
            pendingExecutionValue = if (executed) null else currentValue
        )
    }

    fun peek(ruleId: String): String? {
        return stateByRuleAndKey[ruleId]?.observedValue
    }
}
