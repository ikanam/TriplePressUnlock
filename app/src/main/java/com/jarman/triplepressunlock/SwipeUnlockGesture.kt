package com.jarman.triplepressunlock

internal class SwipeUnlockGesture(minimumDistance: Float) {
    private val minimumDistanceSquared: Float
    private var startX = 0f
    private var startY = 0f
    private var tracking = false

    init {
        require(minimumDistance > 0f) { "minimumDistance must be positive" }
        minimumDistanceSquared = minimumDistance * minimumDistance
    }

    fun start(x: Float, y: Float) {
        startX = x
        startY = y
        tracking = true
    }

    fun finish(x: Float, y: Float): Boolean {
        if (!tracking) return false
        tracking = false
        val deltaX = x - startX
        val deltaY = y - startY
        return deltaX * deltaX + deltaY * deltaY >= minimumDistanceSquared
    }

    fun cancel() {
        tracking = false
    }
}
