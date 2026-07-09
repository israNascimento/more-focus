package br.com.morefocus.blocking

private const val MILLIS_PER_SECOND = 1000L

data class AppLimitRule(
    val packageName: String,
    val appName: String,
    val limitSeconds: Int,
    val usageMillis: Long = 0L,
    val enabled: Boolean = true
) {
    val limitMillis: Long
        get() = limitSeconds.coerceAtLeast(1) * MILLIS_PER_SECOND

    val remainingDailyLimitMillis: Long
        get() = (limitMillis - usageMillis).coerceAtLeast(0L)
}
