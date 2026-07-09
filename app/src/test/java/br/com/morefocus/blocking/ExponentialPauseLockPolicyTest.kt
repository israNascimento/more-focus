package br.com.morefocus.blocking

import org.junit.Assert.assertEquals
import org.junit.Test

class ExponentialPauseLockPolicyTest {
    @Test
    fun followsExpectedSequenceAndCapsAtSixtySeconds() {
        assertEquals(0, ExponentialPauseLockPolicy.lockSeconds(0))
        assertEquals(3, ExponentialPauseLockPolicy.lockSeconds(1))
        assertEquals(9, ExponentialPauseLockPolicy.lockSeconds(2))
        assertEquals(27, ExponentialPauseLockPolicy.lockSeconds(3))
        assertEquals(60, ExponentialPauseLockPolicy.lockSeconds(4))
        assertEquals(60, ExponentialPauseLockPolicy.lockSeconds(Int.MAX_VALUE))
    }

    @Test
    fun incrementCapsWithoutOverflowing() {
        assertEquals(1, ExponentialPauseLockPolicy.increment(0))
        assertEquals(4, ExponentialPauseLockPolicy.increment(3))
        assertEquals(4, ExponentialPauseLockPolicy.increment(4))
        assertEquals(4, ExponentialPauseLockPolicy.increment(Int.MAX_VALUE))
    }
}
