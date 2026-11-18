package br.com.morefocus.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class EventEvaluationThrottleTest {
    @Test
    fun evaluatesFirstValueImmediatelyAndLatestPendingValueAtTrailingEdge() {
        val scheduler = FakeScheduler()
        val evaluatedValues = mutableListOf<String>()
        val throttle = scheduler.throttle(evaluatedValues::add)

        throttle.submit("first")
        scheduler.advanceTo(100L)
        throttle.submit("outdated")
        scheduler.advanceTo(200L)
        throttle.submit("latest")

        assertEquals(listOf("first"), evaluatedValues)

        scheduler.advanceTo(250L)

        assertEquals(listOf("first", "latest"), evaluatedValues)
    }

    @Test
    fun limitsContinuousEventsToOneEvaluationEveryInterval() {
        val scheduler = FakeScheduler()
        val evaluatedValues = mutableListOf<Int>()
        val throttle = scheduler.throttle(evaluatedValues::add)

        throttle.submit(0)
        for (timeMillis in 50L..1_000L step 50) {
            scheduler.advanceTo(timeMillis)
            throttle.submit(timeMillis.toInt())
        }

        assertEquals(listOf(0, 200, 450, 700, 950), evaluatedValues)
    }

    @Test
    fun cancelDropsPendingValueAndRestartsWithAnImmediateEvaluation() {
        val scheduler = FakeScheduler()
        val evaluatedValues = mutableListOf<String>()
        val throttle = scheduler.throttle(evaluatedValues::add)

        throttle.submit("first")
        scheduler.advanceTo(100L)
        throttle.submit("pending")
        throttle.cancel()
        scheduler.advanceTo(250L)
        throttle.submit("after-cancel")

        assertEquals(listOf("first", "after-cancel"), evaluatedValues)
    }

    private class FakeScheduler {
        private data class ScheduledTask(
            val runnable: Runnable,
            val runAtMillis: Long
        )

        var nowMillis: Long = 0L
            private set
        private val scheduledTasks = mutableListOf<ScheduledTask>()

        fun <T : Any> throttle(evaluate: (T) -> Unit): EventEvaluationThrottle<T> =
            EventEvaluationThrottle(
                intervalMillis = 250L,
                currentTimeMillis = { nowMillis },
                postDelayed = { runnable, delayMillis ->
                    scheduledTasks += ScheduledTask(runnable, nowMillis + delayMillis)
                },
                removeCallbacks = { runnable ->
                    scheduledTasks.removeAll { task -> task.runnable === runnable }
                },
                evaluate = evaluate
            )

        fun advanceTo(targetMillis: Long) {
            require(targetMillis >= nowMillis)
            while (true) {
                val nextTask = scheduledTasks
                    .filter { task -> task.runAtMillis <= targetMillis }
                    .minByOrNull { task -> task.runAtMillis }
                    ?: break
                scheduledTasks.remove(nextTask)
                nowMillis = nextTask.runAtMillis
                nextTask.runnable.run()
            }
            nowMillis = targetMillis
        }
    }
}
