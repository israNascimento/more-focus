package br.com.morefocus.accessibility

internal class EventEvaluationThrottle<T : Any>(
    private val intervalMillis: Long,
    private val currentTimeMillis: () -> Long,
    private val postDelayed: (Runnable, Long) -> Unit,
    private val removeCallbacks: (Runnable) -> Unit,
    private val evaluate: (T) -> Unit
) {
    private var lastEvaluationAtMillis: Long? = null
    private var pendingValue: T? = null
    private var pendingEvaluation: Runnable? = null

    init {
        require(intervalMillis > 0L)
    }

    fun submit(value: T) {
        val nowMillis = currentTimeMillis()
        val lastEvaluationAt = lastEvaluationAtMillis
        val elapsedMillis = lastEvaluationAt?.let { nowMillis - it }

        if (elapsedMillis == null || elapsedMillis >= intervalMillis) {
            cancelPendingEvaluation()
            lastEvaluationAtMillis = nowMillis
            evaluate(value)
            return
        }

        pendingValue = value
        if (pendingEvaluation != null) return

        val nextEvaluation = Runnable {
            pendingEvaluation = null
            val latestValue = pendingValue ?: return@Runnable
            pendingValue = null
            lastEvaluationAtMillis = currentTimeMillis()
            evaluate(latestValue)
        }
        pendingEvaluation = nextEvaluation
        postDelayed(nextEvaluation, (intervalMillis - elapsedMillis).coerceAtLeast(0L))
    }

    fun cancel() {
        cancelPendingEvaluation()
        lastEvaluationAtMillis = null
    }

    private fun cancelPendingEvaluation() {
        pendingEvaluation?.let(removeCallbacks)
        pendingEvaluation = null
        pendingValue = null
    }
}
