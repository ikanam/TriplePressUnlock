package com.jarman.triplepressunlock

internal class LockCycleState {
    var isLocked: Boolean = false
        private set

    fun onServiceConnected() {
        isLocked = false
    }

    fun onScreenOff() {
        isLocked = true
    }

    fun lockNow() {
        isLocked = true
    }

    fun unlock() {
        isLocked = false
    }
}
