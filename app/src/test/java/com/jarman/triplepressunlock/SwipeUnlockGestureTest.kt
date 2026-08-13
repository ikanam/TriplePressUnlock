package com.jarman.triplepressunlock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeUnlockGestureTest {
    @Test
    fun deliberateSwipeUnlocksInAnyDirection() {
        val gesture = SwipeUnlockGesture(120f)

        gesture.start(300f, 300f)
        assertTrue(gesture.finish(180f, 300f))

        gesture.start(300f, 300f)
        assertTrue(gesture.finish(300f, 420f))
    }

    @Test
    fun shortMovementDoesNotUnlock() {
        val gesture = SwipeUnlockGesture(120f)

        gesture.start(100f, 100f)
        assertFalse(gesture.finish(150f, 150f))
    }

    @Test
    fun cancelledGestureDoesNotUnlock() {
        val gesture = SwipeUnlockGesture(120f)

        gesture.start(100f, 100f)
        gesture.cancel()
        assertFalse(gesture.finish(300f, 100f))
    }
}
