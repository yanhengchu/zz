package cc.ai.zz.feature.ocr.rule

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileNotFoundException

class OcrRuleRepository(
    private val context: Context
) {
    companion object {
        private const val TAG = "OcrRuleRepository"
        private const val DEFAULT_RULES_ASSET_PATH = "ocr_rules_default.csv"
        private val RULES_ASSET_REGEX = Regex("""ocr_rules_(\d+)x(\d+)\.csv""")
        private const val CONFUSIONS_ASSET_PATH = "ocr_confusions.csv"
        private const val CLEAN_CONFIG_ASSET_PATH = "ocr_clean_config.csv"
        private const val EXTERNAL_RULES_DIR = "ocr"
        private const val EXTERNAL_RULES_FILE_NAME = "ocr_rules.override.csv"
        private const val EXTERNAL_RULES_TEMPLATE = """
id,priority,log,timeout,pkg,keywords,action_type,value_policy,action_target,else_target
exp_back,0,0,,,"广告|领取成功",BACK,,,
"""

        internal fun mergeRules(
            bundledRules: List<OcrActionRule>,
            externalRules: List<OcrActionRule>
        ): List<OcrActionRule> {
            if (externalRules.isEmpty()) return bundledRules.sortedByDescending { it.priority }
            val mergedById = linkedMapOf<String, OcrActionRule>()
            bundledRules.forEach { rule -> mergedById[rule.id] = rule }
            externalRules.forEach { rule -> mergedById[rule.id] = rule }
            return mergedById.values.sortedByDescending { it.priority }
        }

        internal fun mergeRulePatches(
            baseRules: List<OcrActionRule>,
            patches: List<OcrActionRulePatch>
        ): List<OcrActionRule> {
            if (patches.isEmpty()) return baseRules.sortedByDescending { it.priority }
            val patchById = patches.associateBy { it.id }
            val patchedIds = linkedSetOf<String>()
            val mergedRules = baseRules.map { baseRule ->
                val patch = patchById[baseRule.id] ?: return@map baseRule
                patchedIds += baseRule.id
                baseRule.applyPatch(patch)
            }
            patches
                .asSequence()
                .filter { it.id !in patchedIds }
                .forEach { patch ->
                    Log.w(TAG, "ignored OCR resolution override for unknown rule id=${patch.id}")
                }
            return mergedRules.sortedByDescending { it.priority }
        }

        internal fun parseCsvRules(rawCsv: String): List<OcrActionRule> {
            val lines = rawCsv
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()
            if (lines.isEmpty()) return emptyList()
            val header = parseCsvLine(lines.first()).map { it.trim().lowercase() }
            if (header.isEmpty()) return emptyList()
            return buildList {
                lines.drop(1).forEach { line ->
                    val values = parseCsvLine(line)
                    val row = header.mapIndexedNotNull { index, key ->
                        if (index >= values.size) null else key to values[index].trim()
                    }.toMap()
                    parseCsvRule(row)?.let(::add)
                }
            }.sortedByDescending { it.priority }
        }

        internal fun parseCsvRulePatches(rawCsv: String): List<OcrActionRulePatch> {
            val lines = rawCsv
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()
            if (lines.isEmpty()) return emptyList()
            val header = parseCsvLine(lines.first()).map { it.trim().lowercase() }
            if (header.isEmpty()) return emptyList()
            return buildList {
                lines.drop(1).forEach { line ->
                    val values = parseCsvLine(line)
                    val row = header.mapIndexedNotNull { index, key ->
                        if (index >= values.size) null else key to values[index].trim()
                    }.toMap()
                    parseCsvRulePatch(row)?.let(::add)
                }
            }
        }

        internal fun parseCsvConfusions(rawCsv: String): Map<Char, Char> {
            val lines = rawCsv
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .filterNot { it.startsWith("#") }
                .toList()
            if (lines.isEmpty()) return emptyMap()
            val header = parseCsvLine(lines.first()).map { it.trim().lowercase() }
            if (header.isEmpty()) return emptyMap()
            val canonicalIndex = header.indexOf("canonical")
            val variantsIndex = header.indexOf("variants")
            if (canonicalIndex == -1 || variantsIndex == -1) return emptyMap()
            val result = linkedMapOf<Char, Char>()
            lines.drop(1).forEach { line ->
                val values = parseCsvLine(line)
                val canonicalText = values.getOrNull(canonicalIndex)?.trim().orEmpty()
                val variantsText = values.getOrNull(variantsIndex)?.trim().orEmpty()
                val canonical = canonicalText.singleOrNull() ?: return@forEach
                val variants = variantsText
                    .split('|')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                variants.forEach { variant ->
                    variant.forEach { char -> result[char] = canonical }
                }
                result.putIfAbsent(canonical, canonical)
            }
            return result
        }

        internal fun parseCsvCleanConfig(rawCsv: String): OcrTextCleanConfig {
            val lines = rawCsv
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .filterNot { it.startsWith("#") }
                .toList()
            if (lines.isEmpty()) return OcrTextCleanConfig()
            val header = parseCsvLine(lines.first()).map { it.trim().lowercase() }
            if (header.isEmpty()) return OcrTextCleanConfig()
            val typeIndex = header.indexOf("type")
            val valueIndex = header.indexOf("value")
            if (typeIndex == -1 || valueIndex == -1) return OcrTextCleanConfig()

            var removeAsciiPunctuation = true
            var removeAsciiLetters = false
            val removableChars = linkedSetOf<Char>()
            val dropLinePrefixes = mutableListOf<String>()
            val dropLineContains = mutableListOf<String>()
            val dropLineExact = mutableListOf<String>()

            lines.drop(1).forEach { line ->
                val values = parseCsvLine(line)
                val type = values.getOrNull(typeIndex)?.trim().orEmpty().lowercase()
                val value = values.getOrNull(valueIndex)?.trim().orEmpty()
                if (type.isEmpty() || value.isEmpty()) return@forEach
                when (type) {
                    "remove_ascii_punctuation" -> removeAsciiPunctuation = value.toBooleanFlag()
                    "remove_ascii_letters" -> removeAsciiLetters = value.toBooleanFlag()
                    "remove_chars" -> value.forEach(removableChars::add)
                    "drop_line_prefix" -> dropLinePrefixes += value
                    "drop_line_contains" -> dropLineContains += value
                    "drop_line_exact" -> dropLineExact += value
                }
            }

            return OcrTextCleanConfig(
                removeAsciiPunctuation = removeAsciiPunctuation,
                removeAsciiLetters = removeAsciiLetters,
                removableChars = removableChars,
                dropLinePrefixes = dropLinePrefixes,
                dropLineContains = dropLineContains,
                dropLineExact = dropLineExact
            )
        }

        private fun parseCsvRule(row: Map<String, String>): OcrActionRule? {
            val id = row["id"].orEmpty().trim()
            if (id.isEmpty()) return null
            val keywords = row["keywords"].splitPipeList()
            if (keywords.isEmpty()) return null
            val action = parseCsvAction(row) ?: return null
            return OcrActionRule(
                id = id,
                priority = row["priority"]?.toIntOrNull() ?: 0,
                packages = row["pkg"].splitPipeList(),
                keywords = keywords,
                action = action,
                valuePolicy = row["value_policy"]?.trim().toOcrValuePolicyOrNull(),
                timeout = row["timeout"]?.trim().toOcrRuleTimeoutOrNull(),
                log = row["log"].toBooleanFlag()
            )
        }

        private fun parseCsvRulePatch(row: Map<String, String>): OcrActionRulePatch? {
            val id = row["id"].orEmpty().trim()
            if (id.isEmpty()) return null
            return OcrActionRulePatch(
                id = id,
                priority = row["priority"].toIntOrNullOrNull(),
                packages = row["pkg"].toNullablePipeList(),
                keywords = row["keywords"].toNullablePipeList(),
                actionType = row["action_type"].orEmpty().trim().uppercase().ifEmpty { null },
                actionTarget = row["action_target"].toClickTargetOrNull(),
                elseTarget = row["else_target"].toClickTargetOrNull(),
                valuePolicy = row["value_policy"]?.trim().toOcrValuePolicyOrNull(),
                timeout = row["timeout"]?.trim().toOcrRuleTimeoutOrNull(),
                log = row["log"].toBooleanFlagOrNull()
            )
        }

        private fun parseCsvAction(row: Map<String, String>): OcrRuleAction? {
            return when (row["action_type"].orEmpty().trim().uppercase()) {
                "WAIT" -> OcrRuleAction.Wait
                "BACK" -> OcrRuleAction.Back
                "SWIPE" -> OcrRuleAction.Swipe
                "CLICK" -> OcrRuleAction.Click(
                    target = row["action_target"].toClickTargetOrNull() ?: OcrClickTarget(0.5f, 0.5f),
                    elseTarget = row["else_target"].toClickTargetOrNull()
                )

                else -> null
            }
        }

        private fun parseCsvLine(line: String): List<String> {
            val result = mutableListOf<String>()
            val current = StringBuilder()
            var inQuotes = false
            var index = 0
            while (index < line.length) {
                val char = line[index]
                when {
                    char == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                        current.append('"')
                        index += 1
                    }

                    char == '"' -> inQuotes = !inQuotes
                    char == ',' && !inQuotes -> {
                        result.add(current.toString())
                        current.clear()
                    }

                    else -> current.append(char)
                }
                index += 1
            }
            result.add(current.toString())
            return result
        }

        private fun String?.splitPipeList(): List<String> {
            return this.orEmpty()
                .split('|')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

        private fun String?.toNullablePipeList(): List<String>? {
            val raw = this.orEmpty().trim()
            if (raw.isEmpty()) return null
            return raw.split('|')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .takeIf { it.isNotEmpty() }
        }

        private fun String?.toOcrValuePolicyOrNull(): OcrValuePolicy? {
            val raw = this.orEmpty().trim()
            return when (raw.uppercase()) {
                "CHANGED" -> OcrValuePolicy.Changed
                "UNCHANGED" -> OcrValuePolicy.Unchanged
                else -> raw.toNumericThresholdPolicyOrNull()
            }
        }

        private fun String?.toOcrRuleTimeoutOrNull(): OcrRuleTimeout? {
            val raw = this.orEmpty().trim()
            if (raw.isEmpty()) return null
            val parts = raw.split(':', limit = 2)
            if (parts.size != 2) return null
            val awaitRuleId = parts[0].trim()
            val timeoutSeconds = parts[1].trim().toIntOrNull() ?: return null
            if (awaitRuleId.isEmpty() || timeoutSeconds <= 0) return null
            return OcrRuleTimeout(awaitRuleId = awaitRuleId, timeoutSeconds = timeoutSeconds)
        }

        private fun String.toNumericThresholdPolicyOrNull(): OcrValuePolicy.NumericThreshold? {
            val parts = split(':', limit = 2)
            if (parts.size != 2) return null
            val operator = when (parts[0].trim().uppercase()) {
                "LT" -> NumericCompareOperator.LT
                "LTE" -> NumericCompareOperator.LTE
                "GT" -> NumericCompareOperator.GT
                "GTE" -> NumericCompareOperator.GTE
                "EQ" -> NumericCompareOperator.EQ
                else -> return null
            }
            val threshold = parts[1].trim().toIntOrNull() ?: return null
            return OcrValuePolicy.NumericThreshold(operator, threshold)
        }

        private fun String?.toClickTargetOrNull(): OcrClickTarget? {
            val raw = this.orEmpty().trim()
            if (raw.isEmpty()) return null
            val parts = raw.split(':', limit = 2)
            if (parts.size != 2) return null
            val x = parts[0].trim().toFloatOrNull() ?: return null
            val y = parts[1].trim().toFloatOrNull() ?: return null
            return OcrClickTarget(x, y)
        }

        private fun String?.toBooleanFlag(): Boolean {
            return when (this.orEmpty().trim().lowercase()) {
                "1", "true", "yes", "y", "on" -> true
                else -> false
            }
        }

        private fun String?.toBooleanFlagOrNull(): Boolean? {
            val raw = this.orEmpty().trim()
            if (raw.isEmpty()) return null
            return raw.toBooleanFlag()
        }

        private fun String?.toIntOrNullOrNull(): Int? {
            val raw = this.orEmpty().trim()
            if (raw.isEmpty()) return null
            return raw.toIntOrNull()
        }

        internal fun resolveClosestResolutionRulesAssetPath(
            width: Int,
            height: Int,
            assetPaths: List<String>,
            defaultAssetPath: String = DEFAULT_RULES_ASSET_PATH
        ): String? {
            if (assetPaths.isEmpty()) return null
            val exactPath = "ocr_rules_${width}x${height}.csv"
            if (assetPaths.contains(exactPath)) return exactPath
            val targetRatio = width.toFloat() / height.toFloat()
            return assetPaths
                .asSequence()
                .filter { it != defaultAssetPath }
                .mapNotNull { path ->
                    val match = RULES_ASSET_REGEX.matchEntire(path) ?: return@mapNotNull null
                    val assetWidth = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                    val assetHeight = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                    val ratio = assetWidth.toFloat() / assetHeight.toFloat()
                    path to kotlin.math.abs(ratio - targetRatio)
                }
                .minByOrNull { it.second }
                ?.first
        }
    }

    fun loadRules(): List<OcrActionRule> {
        val bundledRules = loadBundledRules()
        val externalRules = loadExternalRules()
        return mergeRules(bundledRules, externalRules)
    }

    fun loadConfusions(): Map<Char, Char> {
        return runCatching {
            context.assets.open(CONFUSIONS_ASSET_PATH).bufferedReader().use { reader ->
                parseCsvConfusions(reader.readText())
            }
        }.onFailure { error ->
            Log.e(TAG, "failed to load OCR confusions from assets/$CONFUSIONS_ASSET_PATH", error)
        }.getOrDefault(emptyMap())
    }

    fun loadCleanConfig(): OcrTextCleanConfig {
        return runCatching {
            context.assets.open(CLEAN_CONFIG_ASSET_PATH).bufferedReader().use { reader ->
                parseCsvCleanConfig(reader.readText())
            }
        }.onFailure { error ->
            Log.e(TAG, "failed to load OCR clean config from assets/$CLEAN_CONFIG_ASSET_PATH", error)
        }.getOrDefault(OcrTextCleanConfig())
    }

    fun getBundledRuleInfo(): OcrBundledRuleInfo {
        val displayMetrics = context.resources.displayMetrics
        val width = displayMetrics.widthPixels.coerceAtLeast(1)
        val height = displayMetrics.heightPixels.coerceAtLeast(1)
        return OcrBundledRuleInfo(
            screenWidth = width,
            screenHeight = height,
            defaultAssetPath = DEFAULT_RULES_ASSET_PATH,
            resolutionAssetPath = resolveResolutionRulesAssetPath()
        )
    }

    fun resolveExternalRulesFile(): File? {
        val dir = context.getExternalFilesDir(EXTERNAL_RULES_DIR) ?: return null
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, EXTERNAL_RULES_FILE_NAME)
    }

    fun ensureExternalRulesFileExists() {
        val externalFile = resolveExternalRulesFile() ?: return
        if (externalFile.exists()) return
        runCatching {
            externalFile.writeText(EXTERNAL_RULES_TEMPLATE)
            Log.d(TAG, "created external OCR rules file path=${externalFile.absolutePath}")
        }.onFailure { error ->
            Log.e(TAG, "failed to create external OCR rules file path=${externalFile.absolutePath}", error)
        }
    }

    private fun loadBundledRules(): List<OcrActionRule> {
        val defaultRules = loadRulesFromAsset(DEFAULT_RULES_ASSET_PATH, missingIsNormal = false)
        val resolutionPath = resolveResolutionRulesAssetPath()
        if (resolutionPath == null || resolutionPath == DEFAULT_RULES_ASSET_PATH) {
            return defaultRules
        }
        val resolutionPatches = loadRulePatchesFromAsset(resolutionPath, missingIsNormal = true)
        return mergeRulePatches(defaultRules, resolutionPatches)
    }

    private fun loadExternalRules(): List<OcrActionRule> {
        return loadExternalCsvRules()
    }

    private fun loadExternalCsvRules(): List<OcrActionRule> {
        val externalFile = resolveExternalRulesFile() ?: return emptyList()
        if (!externalFile.exists()) {
            Log.d(TAG, "external OCR csv rules file does not exist path=${externalFile.absolutePath}")
            return emptyList()
        }
        return runCatching {
            externalFile.bufferedReader().use { reader ->
                parseCsvRules(reader.readText())
            }
        }.onFailure { error ->
            Log.e(TAG, "failed to load external OCR csv rules path=${externalFile.absolutePath}", error)
        }.getOrDefault(emptyList())
    }

    private fun loadRulesFromAsset(path: String, missingIsNormal: Boolean): List<OcrActionRule> {
        return runCatching {
            context.assets.open(path).bufferedReader().use { reader ->
                parseCsvRules(reader.readText())
            }
        }.onFailure { error ->
            if (missingIsNormal && error is FileNotFoundException) {
                Log.d(TAG, "OCR rules asset not found path=assets/$path")
            } else {
                Log.e(TAG, "failed to load OCR rules from assets/$path", error)
            }
        }.getOrDefault(emptyList())
    }

    private fun loadRulePatchesFromAsset(path: String, missingIsNormal: Boolean): List<OcrActionRulePatch> {
        return runCatching {
            context.assets.open(path).bufferedReader().use { reader ->
                parseCsvRulePatches(reader.readText())
            }
        }.onFailure { error ->
            if (missingIsNormal && error is FileNotFoundException) {
                Log.d(TAG, "OCR rules asset not found path=assets/$path")
            } else {
                Log.e(TAG, "failed to load OCR rule patches from assets/$path", error)
            }
        }.getOrDefault(emptyList())
    }

    private fun resolveResolutionRulesAssetPath(): String? {
        val displayMetrics = context.resources.displayMetrics
        val width = displayMetrics.widthPixels.coerceAtLeast(1)
        val height = displayMetrics.heightPixels.coerceAtLeast(1)
        val assetPaths = runCatching {
            context.assets.list("")?.toList().orEmpty()
        }.onFailure { error ->
            Log.e(TAG, "failed to list OCR rules assets", error)
        }.getOrDefault(emptyList())
        val resolvedPath = resolveClosestResolutionRulesAssetPath(width, height, assetPaths)
        if (resolvedPath != null && resolvedPath != "ocr_rules_${width}x${height}.csv") {
            Log.d(TAG, "use closest OCR rules asset path=assets/$resolvedPath for screen=${width}x${height}")
        }
        return resolvedPath
    }
}

data class OcrBundledRuleInfo(
    val screenWidth: Int,
    val screenHeight: Int,
    val defaultAssetPath: String,
    val resolutionAssetPath: String?
)

internal data class OcrActionRulePatch(
    val id: String,
    val priority: Int? = null,
    val packages: List<String>? = null,
    val keywords: List<String>? = null,
    val actionType: String? = null,
    val actionTarget: OcrClickTarget? = null,
    val elseTarget: OcrClickTarget? = null,
    val valuePolicy: OcrValuePolicy? = null,
    val timeout: OcrRuleTimeout? = null,
    val log: Boolean? = null
)

private fun OcrActionRule.applyPatch(patch: OcrActionRulePatch): OcrActionRule {
    return copy(
        priority = patch.priority ?: priority,
        packages = patch.packages ?: packages,
        keywords = patch.keywords ?: keywords,
        action = action.applyPatch(patch),
        valuePolicy = patch.valuePolicy ?: valuePolicy,
        timeout = patch.timeout ?: timeout,
        log = patch.log ?: log
    )
}

private fun OcrRuleAction.applyPatch(patch: OcrActionRulePatch): OcrRuleAction {
    return when (patch.actionType) {
        null -> when (this) {
            is OcrRuleAction.Click -> copy(
                target = patch.actionTarget ?: target,
                elseTarget = patch.elseTarget ?: elseTarget
            )

            else -> this
        }

        "WAIT" -> OcrRuleAction.Wait
        "BACK" -> OcrRuleAction.Back
        "SWIPE" -> OcrRuleAction.Swipe
        "CLICK" -> {
            val baseClick = this as? OcrRuleAction.Click
            OcrRuleAction.Click(
                target = patch.actionTarget ?: baseClick?.target ?: OcrClickTarget(0.5f, 0.5f),
                elseTarget = patch.elseTarget ?: baseClick?.elseTarget
            )
        }

        else -> this
    }
}
