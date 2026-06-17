package cc.ai.zz.feature.automation.config

import android.content.Context

object SwipeRuntimeConfig {
    const val DEFAULT_PERIOD_MS = 3_000L
    val PERIOD_OPTIONS_MS = listOf(3_000L, 10_000L, 30_000L, 50_000L)

    private const val PREFS_NAME = "swipe_runtime_config"
    private const val KEY_PERIOD_MS = "period_ms"

    fun getPeriodMs(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val periodMs = prefs.getLong(KEY_PERIOD_MS, DEFAULT_PERIOD_MS)
        return periodMs.takeIf { it in PERIOD_OPTIONS_MS } ?: DEFAULT_PERIOD_MS
    }

    fun setPeriodMs(context: Context, periodMs: Long) {
        val normalizedPeriodMs = periodMs.takeIf { it in PERIOD_OPTIONS_MS } ?: DEFAULT_PERIOD_MS
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_PERIOD_MS, normalizedPeriodMs)
            .apply()
    }
}
