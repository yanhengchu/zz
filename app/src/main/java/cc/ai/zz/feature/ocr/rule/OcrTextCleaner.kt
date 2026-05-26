package cc.ai.zz.feature.ocr.rule

class OcrTextCleaner(
    private val config: OcrTextCleanConfig = OcrTextCleanConfig()
) {
    fun normalizeRecognizedText(rawText: String): String {
        return rawText
            .lineSequence()
            .map { line -> line.replace(Regex("[\\t\\x0B\\f\\r ]+"), " ").trim() }
            .filter { it.isNotEmpty() }
            .filterNot(::shouldDropLine)
            .joinToString("\n")
            .trim()
    }

    fun normalizeForMatch(text: String, confusionMap: Map<Char, Char> = emptyMap()): String {
        if (text.isEmpty()) return text
        return buildString(text.length) {
            for (char in text) {
                if (char.isWhitespace()) continue
                if (shouldRemoveChar(char)) continue
                append(confusionMap[char] ?: char)
            }
        }
    }

    private fun shouldDropLine(line: String): Boolean {
        if (config.dropLineExact.any { line.equals(it, ignoreCase = true) }) return true
        if (config.dropLinePrefixes.any { line.startsWith(it, ignoreCase = true) }) return true
        if (config.dropLineContains.any { line.contains(it, ignoreCase = true) }) return true
        return false
    }

    private fun shouldRemoveChar(char: Char): Boolean {
        if (char in config.removableChars) return true
        if (config.removeAsciiLetters && char.isConfiguredAsciiLetter()) return true
        return config.removeAsciiPunctuation && char.isConfiguredAsciiPunctuation()
    }

    private fun Char.isConfiguredAsciiPunctuation(): Boolean {
        return this in '!'..'/' ||
            (this in ':'..'@' && this != ':') ||
            this in '['..'`' ||
            this in '{'..'~'
    }

    private fun Char.isConfiguredAsciiLetter(): Boolean {
        return this in 'a'..'z' || this in 'A'..'Z'
    }
}
