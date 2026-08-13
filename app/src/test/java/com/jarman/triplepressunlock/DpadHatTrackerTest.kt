package com.jarman.triplepressunlock

import org.junit.Assert.assertEquals
import org.junit.Test

class DpadHatTrackerTest {
    @Test
    fun recognizesAllFourHatDirections() {
        val tracker = DpadHatTracker()

        assertEquals(DpadHatTracker.UP, tracker.update(0f, -1f))
        tracker.update(0f, 0f)
        assertEquals(DpadHatTracker.DOWN, tracker.update(0f, 1f))
        tracker.update(0f, 0f)
        assertEquals(DpadHatTracker.LEFT, tracker.update(-1f, 0f))
        tracker.update(0f, 0f)
        assertEquals(DpadHatTracker.RIGHT, tracker.update(1f, 0f))
    }

    @Test
    fun heldDirectionOnlyProducesOnePress() {
        val tracker = DpadHatTracker()

        assertEquals(DpadHatTracker.LEFT, tracker.update(-1f, 0f))
        assertEquals(DpadHatTracker.NEUTRAL, tracker.update(-1f, 0f))
        assertEquals(DpadHatTracker.NEUTRAL, tracker.update(-0.9f, 0f))
        tracker.update(0f, 0f)
        assertEquals(DpadHatTracker.LEFT, tracker.update(-1f, 0f))
    }

    @Test
    fun ignoresSmallAxisNoise() {
        val tracker = DpadHatTracker()

        assertEquals(DpadHatTracker.NEUTRAL, tracker.update(0.2f, -0.3f))
    }
}
