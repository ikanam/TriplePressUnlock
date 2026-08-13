package com.jarman.triplepressunlock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockCycleStateTest {
    @Test
    fun connectingServiceDoesNotImmediatelyLock() {
        val state = LockCycleState()

        state.lockNow()
        state.onServiceConnected()

        assertFalse(state.isLocked)
    }

    @Test
    fun screenOffArmsNextScreenOn() {
        val state = LockCycleState()

        state.onServiceConnected()
        state.onScreenOff()

        assertTrue(state.isLocked)
    }

    @Test
    fun unlockClearsArmedState() {
        val state = LockCycleState()

        state.onScreenOff()
        state.unlock()

        assertFalse(state.isLocked)
    }
}
