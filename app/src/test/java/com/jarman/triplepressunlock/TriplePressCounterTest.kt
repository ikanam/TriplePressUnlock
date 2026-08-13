package com.jarman.triplepressunlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriplePressCounterTest {
    @Test
    fun unlocksOnThirdPressWithinMaximumGaps() {
        val counter = TriplePressCounter(3, 1_000L)

        assertFalse(counter.recordPress(96, 1_000L))
        assertFalse(counter.recordPress(96, 1_900L))
        assertTrue(counter.recordPress(96, 2_850L))
        assertEquals(3, counter.count)
    }

    @Test
    fun restartsSequenceAfterGapExpires() {
        val counter = TriplePressCounter(3, 1_000L)

        assertFalse(counter.recordPress(96, 1_000L))
        assertFalse(counter.recordPress(96, 1_500L))
        assertFalse(counter.recordPress(96, 2_501L))
        assertEquals(1, counter.count)
    }

    @Test
    fun acceptsPressAtExactGapBoundary() {
        val counter = TriplePressCounter(3, 1_000L)

        assertFalse(counter.recordPress(96, 1_000L))
        assertFalse(counter.recordPress(96, 2_000L))
        assertTrue(counter.recordPress(96, 3_000L))
    }

    @Test
    fun changingButtonStartsNewSequence() {
        val counter = TriplePressCounter(3, 1_000L)

        assertFalse(counter.recordPress(96, 1_000L))
        assertFalse(counter.recordPress(96, 1_200L))
        assertFalse(counter.recordPress(97, 1_400L))
        assertEquals(1, counter.count)
        assertFalse(counter.recordPress(97, 1_600L))
        assertTrue(counter.recordPress(97, 1_800L))
    }

    @Test
    fun resetClearsPartialSequence() {
        val counter = TriplePressCounter(3, 1_000L)

        counter.recordPress(96, 1_000L)
        counter.recordPress(96, 1_200L)
        counter.reset()

        assertEquals(0, counter.count)
        assertFalse(counter.recordPress(96, 1_300L))
        assertEquals(1, counter.count)
    }
}
