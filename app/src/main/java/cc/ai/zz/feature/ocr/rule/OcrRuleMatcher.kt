package cc.ai.zz.feature.ocr.rule

class OcrRuleMatcher(
    private val confusionMap: Map<Char, Char> = emptyMap(),
    private val textCleaner: OcrTextCleaner = OcrTextCleaner()
) {
    companion object {
        private const val MATCH_ALL_KEYWORD = "ALL"
        private const val MATCH_TIMEOUT_ALL_KEYWORD = "TIMEOUT_ALL"
        private const val TIME_PLACEHOLDER = "mm:ss"
        private const val NUMBER_PLACEHOLDER = "num"
        private const val MATCH_ALL_KEYWORD_SENTINEL = "占位全匹配"
        private const val MATCH_TIMEOUT_ALL_KEYWORD_SENTINEL = "占位超时全匹配"
        private const val TIME_PLACEHOLDER_SENTINEL = "占位时间"
        private const val NUMBER_PLACEHOLDER_SENTINEL = "占位数字"
    }

    data class AliasMatch(
        val dynamicValue: String? = null
    )

    data class OcrRuleMatch(
        val rule: OcrActionRule,
        val dynamicValue: String? = null
    )

    fun findFirstMatch(
        rules: List<OcrActionRule>,
        text: String,
        packageName: String = "",
        timeoutTriggeredRuleIds: Set<String> = emptySet()
    ): OcrRuleMatch? {
        val normalizedText = text.trim()
        if (normalizedText.isEmpty()) return null
        val matchText = textCleaner.normalizeForMatch(normalizedText, confusionMap)
        return rules.firstNotNullOfOrNull { rule ->
            if (!rule.matchesPackage(packageName)) return@firstNotNullOfOrNull null
            rule.match(matchText, timeoutTriggeredRuleIds)
        }
    }

    private fun OcrActionRule.matchesPackage(packageName: String): Boolean {
        if (packages.isEmpty()) return true
        if (packageName.isBlank()) return false
        return packages.any { it.equals(packageName, ignoreCase = true) }
    }

    private fun OcrActionRule.match(text: String, timeoutTriggeredRuleIds: Set<String>): OcrRuleMatch? {
        var dynamicValue: String? = null
        keywords.forEach { keyword ->
            val aliasMatch = keyword.matchesAnyAlias(
                text = text,
                allowTimeoutWildcard = id in timeoutTriggeredRuleIds
            ) ?: return null
            if (dynamicValue == null) {
                dynamicValue = aliasMatch.dynamicValue
            }
        }
        return OcrRuleMatch(rule = this, dynamicValue = dynamicValue)
    }

    private fun String.matchesAnyAlias(text: String, allowTimeoutWildcard: Boolean): AliasMatch? {
        val aliases = splitAliases().map { rawAlias ->
            rawAlias to rawAlias.normalizeAliasForMatch()
        }.filter { (_, normalizedAlias) -> normalizedAlias.isNotEmpty() }
        if (aliases.isEmpty()) return null
        aliases.forEach { (rawAlias, normalizedAlias) ->
            if (rawAlias.equals(MATCH_ALL_KEYWORD, ignoreCase = true)) {
                return AliasMatch()
            } else if (rawAlias.equals(MATCH_TIMEOUT_ALL_KEYWORD, ignoreCase = true)) {
                if (allowTimeoutWildcard) {
                    return AliasMatch()
                }
            } else if (normalizedAlias.contains(TIME_PLACEHOLDER, ignoreCase = true)) {
                val extractedValue = normalizedAlias.extractTimePlaceholderValue(text)
                if (extractedValue != null) {
                    return AliasMatch(dynamicValue = extractedValue)
                }
            } else if (normalizedAlias.contains(NUMBER_PLACEHOLDER, ignoreCase = true)) {
                val extractedValue = normalizedAlias.extractNumberPlaceholderValue(text)
                if (extractedValue != null) {
                    return AliasMatch(dynamicValue = extractedValue)
                }
            } else {
                if (text.contains(normalizedAlias, ignoreCase = true)) {
                    return AliasMatch()
                }
            }
        }
        return null
    }

    private fun String.normalizeAliasForMatch(): String {
        return replace(MATCH_TIMEOUT_ALL_KEYWORD, MATCH_TIMEOUT_ALL_KEYWORD_SENTINEL, ignoreCase = true)
            .replace(MATCH_ALL_KEYWORD, MATCH_ALL_KEYWORD_SENTINEL, ignoreCase = true)
            .replace(TIME_PLACEHOLDER, TIME_PLACEHOLDER_SENTINEL, ignoreCase = true)
            .replace(NUMBER_PLACEHOLDER, NUMBER_PLACEHOLDER_SENTINEL, ignoreCase = true)
            .let { alias -> textCleaner.normalizeForMatch(alias, confusionMap) }
            .replace(MATCH_TIMEOUT_ALL_KEYWORD_SENTINEL, MATCH_TIMEOUT_ALL_KEYWORD)
            .replace(MATCH_ALL_KEYWORD_SENTINEL, MATCH_ALL_KEYWORD)
            .replace(TIME_PLACEHOLDER_SENTINEL, TIME_PLACEHOLDER)
            .replace(NUMBER_PLACEHOLDER_SENTINEL, NUMBER_PLACEHOLDER)
    }

    private fun String.splitAliases(): List<String> {
        if (isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        val current = StringBuilder(length)
        var escaping = false
        for (char in this) {
            if (escaping) {
                current.append(char)
                escaping = false
                continue
            }
            when (char) {
                '\\' -> escaping = true
                '/' -> {
                    result += current.toString()
                    current.setLength(0)
                }

                else -> current.append(char)
            }
        }
        if (escaping) {
            current.append('\\')
        }
        result += current.toString()
        return result
    }

    private fun String.extractTimePlaceholderValue(text: String): String? {
        return toTimePlaceholderRegex().find(text)?.groupValues?.getOrNull(1)?.replace('：', ':')
    }

    private fun String.extractNumberPlaceholderValue(text: String): String? {
        return toNumberPlaceholderRegex().find(text)?.groupValues?.getOrNull(1)
    }

    private fun String.toTimePlaceholderRegex(): Regex {
        val parts = split(TIME_PLACEHOLDER, ignoreCase = true)
        val pattern = buildString {
            parts.forEachIndexed { index, part ->
                append(Regex.escape(part))
                if (index != parts.lastIndex) {
                    append("""(\d+[:：]\d{2})""")
                }
            }
        }
        return Regex(pattern, RegexOption.IGNORE_CASE)
    }

    private fun String.toNumberPlaceholderRegex(): Regex {
        val parts = split(NUMBER_PLACEHOLDER, ignoreCase = true)
        val pattern = buildString {
            parts.forEachIndexed { index, part ->
                append(Regex.escape(part))
                if (index != parts.lastIndex) {
                    append("""(\d+)""")
                }
            }
        }
        return Regex(pattern, RegexOption.IGNORE_CASE)
    }
}
