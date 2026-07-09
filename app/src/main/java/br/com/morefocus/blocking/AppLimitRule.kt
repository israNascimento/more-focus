package br.com.morefocus.blocking

private const val MILLIS_PER_MINUTE = 60 * 1000L

data class AppLimitRule(
    val packageName: String,
    val appName: String,
    val limitMinutes: Int,
    val usageMillis: Long = 0L,
    val enabled: Boolean = true
) {
    val limitMillis: Long
        get() = limitMinutes.coerceAtLeast(1) * MILLIS_PER_MINUTE

    val remainingDailyLimitMillis: Long
        get() = (limitMillis - usageMillis).coerceAtLeast(0L)
}
