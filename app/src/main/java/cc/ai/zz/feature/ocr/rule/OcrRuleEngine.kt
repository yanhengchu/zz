package cc.ai.zz.feature.ocr.rule

import android.content.Context
import android.util.Log
import cc.ai.zz.app.MyApp
import cc.ai.zz.feature.automation.executor.GestureExecutor

private const val OCR_RULE_ENGINE_TAG = "OcrRuleEngine"

class OcrRuleEngine(
    context: Context,
    executorProvider: () -> GestureExecutor?,
    onShowMessage: (String) -> Unit
) {
    companion object {
        private const val TAG = OCR_RULE_ENGINE_TAG
    }

    private val repository = OcrRuleRepository(context)
    private var textCleaner = OcrTextCleaner()
    private var matcher = OcrRuleMatcher(textCleaner = textCleaner)
    private val dynamicValueGate = OcrDynamicValueGate()
    private val timeoutGate = OcrRuleTimeoutGate()
    private val actionCooldownGate = OcrActionCooldownGate()
    private val actionExecutor = OcrActionExecutor(executorProvider, onShowMessage)
    private var rules: List<OcrActionRule> = emptyList()

    fun reloadRules() {
        rules = repository.loadRules()
        textCleaner = OcrTextCleaner(repository.loadCleanConfig())
        matcher = OcrRuleMatcher(repository.loadConfusions(), textCleaner)
        val externalPath = repository.resolveExternalRulesFile()?.absolutePath
        Log.d(TAG, "reloaded OCR rules count=${rules.size} externalPath=$externalPath")
    }

    fun normalizeRecognizedText(rawText: String): String {
        return textCleaner.normalizeRecognizedText(rawText)
    }

    fun resetRuntimeState() {
        dynamicValueGate.reset()
        timeoutGate.reset()
        actionCooldownGate.reset()
    }

    fun handleRecognizedText(
        packageName: String,
        text: String,
        rawTextForDebug: String = text
    ): OcrRuleHandleResult {
        if (rules.isEmpty()) {
            reloadRules()
        }
        return handleRecognizedTextWithRules(
            rules = rules,
            packageName = packageName,
            text = text,
            rawTextForDebug = rawTextForDebug,
            matcher = matcher,
            timeoutGate = timeoutGate,
            actionCooldownGate = actionCooldownGate,
            dynamicValueGate = dynamicValueGate,
            executeRule = { rule, useElseTarget ->
                actionExecutor.execute(rule, useElseTarget)
            },
            logClickDecision = ::logClickDecision,
            onLog = { message -> Log.d(TAG, message) }
        )
    }
}

internal fun handleRecognizedTextWithRules(
    rules: List<OcrActionRule>,
    packageName: String,
    text: String,
    rawTextForDebug: String = text,
    matcher: OcrRuleMatcher,
    timeoutGate: OcrRuleTimeoutGate,
    actionCooldownGate: OcrActionCooldownGate,
    dynamicValueGate: OcrDynamicValueGate,
    executeRule: (rule: OcrActionRule, useElseTarget: Boolean) -> Boolean,
    logClickDecision: (rule: OcrActionRule, match: OcrRuleMatcher.OcrRuleMatch, decision: OcrDynamicValueGate.Decision) -> Unit,
    onLog: (String) -> Unit = {}
): OcrRuleHandleResult {
    val activeRules = timeoutGate.filterActiveRules(rules)
    val triggeredRuleIds = timeoutGate.getTriggeredRuleIds()
    var shouldLogText = false
    val selection = selectRuleInOrder(activeRules) { rule ->
        val match = matcher.findFirstMatch(
            rules = listOf(rule),
            text = text,
            packageName = packageName,
            timeoutTriggeredRuleIds = triggeredRuleIds
        ) ?: return@selectRuleInOrder OcrRuleTraversalResult.NoMatch
        shouldLogText = shouldLogText || rule.log
        timeoutGate.onRuleMatched(rule.id)
        onLog("matched OCR rule id=${rule.id} pkg=$packageName")
        if (rule.valuePolicy != null && match.dynamicValue == null) {
            onLog("dynamic comparison fallback to ordinary execution rule=${rule.id}")
            if (rule.log && rule.valuePolicy is OcrValuePolicy.NumericThreshold) {
                onLog(
                    buildString {
                        append("ocr numeric debug rule=")
                        append(rule.id)
                        append("\nocr_raw_text=")
                        append(rawTextForDebug)
                        append("\nocr_clean_text=")
                        append(text)
                        append("\nocr_match_text=")
                        append(matcher.debugNormalizedText(text))
                        append("\nrule_match_keywords=")
                        append(matcher.debugRuleKeywords(rule).joinToString(" | "))
                    }
                )
            }
        }
        val decision = dynamicValueGate.evaluate(match)
        logClickDecision(rule, match, decision)
        dynamicValueGate.observe(decision)
        if (!decision.shouldExecute) {
            onLog(
                "skip OCR action by dynamic policy rule=${rule.id} valuePolicy=${rule.valuePolicy} current=${match.dynamicValue}"
            )
            return@selectRuleInOrder OcrRuleTraversalResult.Skip(rule.id)
        }
        if (actionCooldownGate.shouldBlock(rule)) {
            onLog("skip OCR action by cooldown rule=${rule.id}")
            return@selectRuleInOrder OcrRuleTraversalResult.Skip(rule.id)
        }
        val executed = executeRule(rule, decision.useElseTarget)
        dynamicValueGate.onActionResult(decision, executed)
        timeoutGate.onRuleExecuted(rule, executed)
        actionCooldownGate.onRuleExecuted(rule, executed)
        if (!executed) {
            onLog(
                "mark OCR dynamic value as pending because action did not execute rule=${rule.id} current=${match.dynamicValue}"
            )
            return@selectRuleInOrder OcrRuleTraversalResult.Fail(rule.id)
        }
        OcrRuleTraversalResult.Execute(
            ruleId = rule.id,
            displayStatus = rule.toDisplayStatus(decision)
        )
    }
    return when (selection) {
        null -> OcrRuleHandleResult(status = "none")
        is OcrRuleTraversalResult.Execute -> OcrRuleHandleResult(status = selection.displayStatus, shouldLogText = shouldLogText)
        is OcrRuleTraversalResult.Fail -> OcrRuleHandleResult(status = "fail:${selection.ruleId}", shouldLogText = shouldLogText)
        is OcrRuleTraversalResult.Skip -> OcrRuleHandleResult(status = "skip:${selection.ruleId}", shouldLogText = shouldLogText)
        OcrRuleTraversalResult.NoMatch -> OcrRuleHandleResult(status = "none")
    }
}

private fun OcrRuleEngine.logClickDecision(
    rule: OcrActionRule,
    match: OcrRuleMatcher.OcrRuleMatch,
    decision: OcrDynamicValueGate.Decision
) {
    val clickAction = rule.action as? OcrRuleAction.Click
    if (clickAction == null) return
    val displayMetrics = MyApp.context.resources.displayMetrics
    val screenSize = "${displayMetrics.widthPixels}x${displayMetrics.heightPixels}"
    val selectedTarget = when {
        decision.useElseTarget -> clickAction.elseTarget?.let { "${it.xRatio}:${it.yRatio}" } ?: "none"
        else -> "${clickAction.target.xRatio}:${clickAction.target.yRatio}"
    }
    val extracted = when {
        rule.valuePolicy is OcrValuePolicy.NumericThreshold && match.dynamicValue == null -> "missing"
        match.dynamicValue != null -> match.dynamicValue
        else -> "none"
    }
    val policy = when (val valuePolicy = rule.valuePolicy) {
        is OcrValuePolicy.NumericThreshold -> "${valuePolicy.operator}:${valuePolicy.threshold}"
        OcrValuePolicy.Changed -> "CHANGED"
        OcrValuePolicy.Unchanged -> "UNCHANGED"
        null -> "none"
    }
    Log.d(
        OCR_RULE_ENGINE_TAG,
        "ocr click decision rule=${rule.id} screen=$screenSize extracted=$extracted policy=$policy useElseTarget=${decision.useElseTarget} target=$selectedTarget"
    )
}

internal sealed interface OcrRuleTraversalResult {
    data object NoMatch : OcrRuleTraversalResult

    data class Execute(
        val ruleId: String,
        val displayStatus: String = ruleId
    ) : OcrRuleTraversalResult

    data class Fail(val ruleId: String) : OcrRuleTraversalResult

    data class Skip(val ruleId: String) : OcrRuleTraversalResult
}

internal fun <T> selectRuleInOrder(
    items: List<T>,
    evaluate: (T) -> OcrRuleTraversalResult
): OcrRuleTraversalResult? {
    var lastSkipped: OcrRuleTraversalResult.Skip? = null

    items.forEach { item ->
        when (val result = evaluate(item)) {
            OcrRuleTraversalResult.NoMatch -> Unit
            is OcrRuleTraversalResult.Execute -> return result
            is OcrRuleTraversalResult.Fail -> return result
            is OcrRuleTraversalResult.Skip -> lastSkipped = result
        }
    }

    return lastSkipped
}

private fun OcrActionRule.toDisplayStatus(decision: OcrDynamicValueGate.Decision): String {
    val clickAction = action as? OcrRuleAction.Click ?: return id
    if (clickAction.elseTarget == null) return id
    return if (decision.useElseTarget) "$id/else" else "$id/action"
}
