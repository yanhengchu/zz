package cc.ai.zz.feature.ocr.rule

data class OcrTextCleanConfig(
    val removeAsciiPunctuation: Boolean = true,
    val removeAsciiLetters: Boolean = false,
    val removableChars: Set<Char> = DEFAULT_REMOVABLE_CHARS,
    val dropLinePrefixes: List<String> = emptyList(),
    val dropLineContains: List<String> = emptyList(),
    val dropLineExact: List<String> = emptyList()
) {
    companion object {
        val DEFAULT_REMOVABLE_CHARS: Set<Char> = setOf(
            '，', '。', '、', '；', '！', '？', '·', '（', '）'
        )
    }
}
