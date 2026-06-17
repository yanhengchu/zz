package cc.ai.zz.feature.ocr.config

import android.content.Context

object OcrRuntimeConfig {
    const val DEFAULT_AD_NEXT_THRESHOLD = 100
    const val AD_NEXT_THRESHOLD_KEY = "AD_NEXT_THRESHOLD"
    val AD_NEXT_THRESHOLD_OPTIONS = listOf(100, 200, 300)

    private const val PREFS_NAME = "ocr_runtime_config"
    private const val KEY_AD_NEXT_THRESHOLD = "ad_next_threshold"

    fun getAdNextThreshold(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val threshold = prefs.getInt(KEY_AD_NEXT_THRESHOLD, DEFAULT_AD_NEXT_THRESHOLD)
        return threshold.takeIf { it in AD_NEXT_THRESHOLD_OPTIONS } ?: DEFAULT_AD_NEXT_THRESHOLD
    }

    fun setAdNextThreshold(context: Context, threshold: Int) {
        val normalizedThreshold = threshold.takeIf { it in AD_NEXT_THRESHOLD_OPTIONS } ?: DEFAULT_AD_NEXT_THRESHOLD
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_AD_NEXT_THRESHOLD, normalizedThreshold)
            .apply()
    }
}
