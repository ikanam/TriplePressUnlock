package com.jarman.triplepressunlock

import kotlin.math.abs

internal class DpadHatTracker {
    private var heldDirection = NEUTRAL

    fun update(horizontal: Float, vertical: Float): Int {
        val direction = directionOf(horizontal, vertical)
        if (direction == NEUTRAL) {
            heldDirection = NEUTRAL
            return NEUTRAL
        }
        if (direction == heldDirection) return NEUTRAL
        heldDirection = direction
        return direction
    }

    fun reset() {
        heldDirection = NEUTRAL
    }

    private fun directionOf(horizontal: Float, vertical: Float): Int {
        val absoluteHorizontal = abs(horizontal)
        val absoluteVertical = abs(vertical)
        if (absoluteHorizontal < PRESS_THRESHOLD && absoluteVertical < PRESS_THRESHOLD) {
            return NEUTRAL
        }
        return if (absoluteHorizontal > absoluteVertical) {
            if (horizontal < 0f) LEFT else RIGHT
        } else {
            if (vertical < 0f) UP else DOWN
        }
    }

    companion object {
        const val NEUTRAL = 0
        const val UP = 1
        const val DOWN = 2
        const val LEFT = 3
        const val RIGHT = 4
        private const val PRESS_THRESHOLD = 0.5f
    }
}
