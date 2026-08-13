package com.jarman.triplepressunlock

internal class TriplePressCounter(
    private val requiredPresses: Int,
    private val maximumGapMillis: Long,
) {
    var count: Int = 0
        private set
    private var keyCode = 0
    private var lastPressAt = 0L

    init {
        require(requiredPresses > 0) { "requiredPresses must be positive" }
        require(maximumGapMillis >= 0L) { "maximumGapMillis must not be negative" }
    }

    fun recordPress(pressedKeyCode: Int, elapsedRealtimeMillis: Long): Boolean {
        count = if (
            count == 0 ||
            pressedKeyCode != keyCode ||
            elapsedRealtimeMillis - lastPressAt > maximumGapMillis
        ) {
            1
        } else {
            count + 1
        }
        keyCode = pressedKeyCode
        lastPressAt = elapsedRealtimeMillis
        return count >= requiredPresses
    }

    fun reset() {
        count = 0
        keyCode = 0
        lastPressAt = 0L
    }
}
