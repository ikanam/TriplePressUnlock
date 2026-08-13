package com.jarman.triplepressunlock

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.lang.ref.WeakReference
import kotlin.math.min
import kotlin.math.roundToInt

class UnlockAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pressCounter = TriplePressCounter(3, MAX_PRESS_GAP_MS)
    private val lockCycleState = LockCycleState()
    private var windowManager: WindowManager? = null
    private var powerManager: PowerManager? = null
    private var lockOverlay: View? = null
    private var progressView: TextView? = null
    private var lastDpadSignalKey = KeyEvent.KEYCODE_UNKNOWN
    private var lastDpadSignalAt = 0L
    private var lastDpadSignalFromHat = false
    private var screenReceiverRegistered = false

    private val resetPresses = Runnable(::resetPressProgress)
    private val autoScreenOff = Runnable(::requestSystemScreenOff)

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    cancelAutoScreenOff()
                    armForNextScreenOn()
                }

                Intent.ACTION_SCREEN_ON -> if (lockCycleState.isLocked) {
                    showLockOverlay()
                    scheduleAutoScreenOff()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        connectedInstance = WeakReference(this)
        lockCycleState.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        powerManager = getSystemService(POWER_SERVICE) as PowerManager

        serviceInfo?.let { info ->
            info.eventTypes = 0
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            serviceInfo = info
        }

        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, screenFilter)
        }
        screenReceiverRegistered = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Intentionally empty. This service never inspects application windows.
    }

    override fun onInterrupt() {
        resetPressProgress()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!lockCycleState.isLocked) return false

        val keyCode = event.keyCode
        if (isPowerControlKey(keyCode)) return false

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val unlockKey = canonicalUnlockButton(keyCode)
            if (unlockKey != KeyEvent.KEYCODE_UNKNOWN) {
                recordUnlockPress(unlockKey)
            } else {
                resetPressProgress()
            }
        }

        // Consume both DOWN and UP so no input leaks into the launcher underneath.
        return true
    }

    override fun onUnbind(intent: Intent): Boolean {
        if (connectedService() === this) connectedInstance = WeakReference(null)
        removeLockOverlay()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (connectedService() === this) connectedInstance = WeakReference(null)
        if (screenReceiverRegistered) {
            unregisterReceiver(screenReceiver)
            screenReceiverRegistered = false
        }
        mainHandler.removeCallbacksAndMessages(null)
        removeLockOverlay()
        super.onDestroy()
    }

    private fun armForNextScreenOn() {
        lockCycleState.onScreenOff()
        resetPressProgress()
        (lockOverlay as? LockOverlayView)?.resetControllerState()
    }

    private fun armLock() {
        lockCycleState.lockNow()
        resetPressProgress()
        showLockOverlay()
        scheduleAutoScreenOff()
    }

    private fun unlock(hapticFeedback: Boolean) {
        if (hapticFeedback) {
            lockOverlay?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
        lockCycleState.unlock()
        cancelAutoScreenOff()
        resetPressProgress()
        removeLockOverlay()
    }

    private fun recordUnlockPress(keyCode: Int, fromHatAxis: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (isDpadKey(keyCode)) {
            if (
                keyCode == lastDpadSignalKey &&
                fromHatAxis != lastDpadSignalFromHat &&
                now - lastDpadSignalAt <= DUPLICATE_DPAD_SIGNAL_MS
            ) {
                return
            }
            lastDpadSignalKey = keyCode
            lastDpadSignalAt = now
            lastDpadSignalFromHat = fromHatAxis
        }
        val complete = pressCounter.recordPress(keyCode, now)

        scheduleAutoScreenOff()
        mainHandler.removeCallbacks(resetPresses)
        updateProgress()
        lockOverlay?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

        if (complete) {
            unlock(true)
        } else {
            mainHandler.postDelayed(resetPresses, MAX_PRESS_GAP_MS)
        }
    }

    private fun resetPressProgress() {
        mainHandler.removeCallbacks(resetPresses)
        pressCounter.reset()
        lastDpadSignalKey = KeyEvent.KEYCODE_UNKNOWN
        lastDpadSignalAt = 0L
        lastDpadSignalFromHat = false
        updateProgress()
    }

    private fun updateProgress() {
        val view = progressView ?: return
        view.text = buildString {
            repeat(3) { index ->
                if (index > 0) append("  ")
                append(if (index < pressCounter.count) "●" else "○")
            }
        }
    }

    private fun scheduleAutoScreenOff() {
        mainHandler.removeCallbacks(autoScreenOff)
        if (!lockCycleState.isLocked || !isScreenInteractive()) return
        mainHandler.postDelayed(autoScreenOff, AUTO_SCREEN_OFF_MS)
    }

    private fun cancelAutoScreenOff() {
        mainHandler.removeCallbacks(autoScreenOff)
    }

    private fun requestSystemScreenOff() {
        if (!lockCycleState.isLocked || !isScreenInteractive()) return

        val accepted =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        if (!accepted) {
            Log.w(TAG, "System rejected the accessibility lock-screen action")
        }
    }

    private fun isScreenInteractive(): Boolean = powerManager?.isInteractive == true

    private fun showLockOverlay() {
        if (lockOverlay != null) return
        val manager = windowManager ?: return

        val overlay = createLockOverlay()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.FILL
            setTitle("TriplePress Unlock")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            manager.addView(overlay, params)
            lockOverlay = overlay
            overlay.requestFocus()
            updateProgress()
        } catch (exception: RuntimeException) {
            progressView = null
            Log.e(TAG, "Unable to add lock overlay", exception)
        }
    }

    private fun createLockOverlay(): View {
        val appearance = LockAppearanceSettings.load(this)
        val root = LockOverlayView().apply {
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            keepScreenOn = false
            systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            setBackgroundColor(LockAppearanceSettings.DEFAULT_BACKGROUND_COLOR)
        }

        appearance.backgroundImageUri?.let { uri ->
            val backgroundImage = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            root.addView(
                backgroundImage,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            loadBackgroundImageAsync(root, backgroundImage, uri)
        }

        val homeIcon = HomeIconView(appearance.iconColor)
        val homeParams = FrameLayout.LayoutParams(dp(124), dp(124), Gravity.CENTER).apply {
            bottomMargin = dp(18)
        }
        root.addView(homeIcon, homeParams)

        val unlockPrompt = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val hint = lockText(
            getString(R.string.lock_prompt),
            20,
            appearance.textColor,
        ).apply { setSingleLine() }
        unlockPrompt.addView(
            hint,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val swipeHint = lockText(
            getString(R.string.swipe_prompt),
            13,
            withAlpha(appearance.textColor, 0xBF),
        ).apply { setSingleLine() }
        unlockPrompt.addView(swipeHint, centeredTopMargin(4))

        val progress = lockText("○  ○  ○", 25, appearance.textColor).apply {
            letterSpacing = 0.04f
        }
        progressView = progress
        unlockPrompt.addView(progress, centeredTopMargin(8))

        val promptParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM,
        ).apply {
            leftMargin = dp(24)
            rightMargin = dp(24)
            bottomMargin = dp(40)
        }
        root.addView(unlockPrompt, promptParams)

        return root
    }

    private fun loadBackgroundImageAsync(root: View, imageView: ImageView, uri: String) {
        val metrics = resources.displayMetrics
        val targetWidth = metrics.widthPixels
        val targetHeight = metrics.heightPixels
        Thread(
            {
                val bitmap = LockBackgroundImageLoader.load(this, uri, targetWidth, targetHeight)
                    ?: return@Thread
                mainHandler.post {
                    if (lockOverlay === root || root.isAttachedToWindow) {
                        imageView.setImageBitmap(bitmap)
                    } else {
                        bitmap.recycle()
                    }
                }
            },
            "LockBackgroundLoader",
        ).start()
    }

    private fun removeLockOverlay() {
        val overlay = lockOverlay
        lockOverlay = null
        progressView = null
        cancelAutoScreenOff()
        val manager = windowManager
        if (overlay != null && manager != null) {
            try {
                manager.removeViewImmediate(overlay)
            } catch (exception: RuntimeException) {
                Log.w(TAG, "Unable to remove lock overlay", exception)
            }
        }
    }

    private fun isPowerControlKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_POWER ||
            keyCode == KeyEvent.KEYCODE_SLEEP ||
            keyCode == KeyEvent.KEYCODE_WAKEUP

    private fun canonicalUnlockButton(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_X,
        KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_BUTTON_R1,
        KeyEvent.KEYCODE_BUTTON_R2,
        KeyEvent.KEYCODE_BUTTON_L1,
        KeyEvent.KEYCODE_BUTTON_L2,
        KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_BUTTON_START,
        -> keyCode

        KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP -> KeyEvent.KEYCODE_DPAD_UP
        KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
        KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
        else -> KeyEvent.KEYCODE_UNKNOWN
    }

    private fun isDpadKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_DPAD_UP ||
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT

    private fun lockText(value: String, sizeSp: Int, color: Int): TextView = TextView(this).apply {
        text = value
        textSize = sizeSp.toFloat()
        setTextColor(color)
        gravity = Gravity.CENTER
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(
        alpha,
        Color.red(color),
        Color.green(color),
        Color.blue(color),
    )

    private fun centeredTopMargin(marginDp: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(marginDp)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private inner class LockOverlayView : FrameLayout(this@UnlockAccessibilityService) {
        private val swipeGesture = SwipeUnlockGesture(dp(SWIPE_UNLOCK_DISTANCE_DP).toFloat())
        private val dpadHatTracker = DpadHatTracker()

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (event.pointerCount != 1) return true
                    swipeGesture.start(event.rawX, event.rawY)
                    cancelAutoScreenOff()
                }

                MotionEvent.ACTION_POINTER_DOWN -> swipeGesture.cancel()

                MotionEvent.ACTION_UP -> {
                    val shouldUnlock = swipeGesture.finish(event.rawX, event.rawY)
                    performClick()
                    if (shouldUnlock) unlock(true) else scheduleAutoScreenOff()
                }

                MotionEvent.ACTION_CANCEL -> {
                    swipeGesture.cancel()
                    scheduleAutoScreenOff()
                }
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        fun resetControllerState() {
            dpadHatTracker.reset()
        }

        override fun onGenericMotionEvent(event: MotionEvent): Boolean {
            val source = event.source
            val isGameController =
                source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                    source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
                    source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
            if (!isGameController) return true

            var horizontal = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            var vertical = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            if (
                horizontal == 0f &&
                vertical == 0f &&
                source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
            ) {
                horizontal = event.getAxisValue(MotionEvent.AXIS_X)
                vertical = event.getAxisValue(MotionEvent.AXIS_Y)
            }

            when (dpadHatTracker.update(horizontal, vertical)) {
                DpadHatTracker.UP -> recordUnlockPress(KeyEvent.KEYCODE_DPAD_UP, true)
                DpadHatTracker.DOWN -> recordUnlockPress(KeyEvent.KEYCODE_DPAD_DOWN, true)
                DpadHatTracker.LEFT -> recordUnlockPress(KeyEvent.KEYCODE_DPAD_LEFT, true)
                DpadHatTracker.RIGHT -> recordUnlockPress(KeyEvent.KEYCODE_DPAD_RIGHT, true)
            }
            return true
        }
    }

    private inner class HomeIconView(private val iconColor: Int) : View(this@UnlockAccessibilityService) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val housePath = Path()

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val viewWidth = width.toFloat()
            val viewHeight = height.toFloat()
            val size = min(viewWidth, viewHeight)
            val centerX = viewWidth / 2f
            val centerY = viewHeight / 2f

            paint.color = iconColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = size * 0.052f
            canvas.drawCircle(centerX, centerY, size * 0.445f, paint)

            paint.style = Paint.Style.FILL
            housePath.reset()
            housePath.fillType = Path.FillType.EVEN_ODD
            housePath.moveTo(centerX, viewHeight * 0.275f)
            housePath.lineTo(viewWidth * 0.235f, viewHeight * 0.485f)
            housePath.quadTo(
                viewWidth * 0.215f,
                viewHeight * 0.505f,
                viewWidth * 0.25f,
                viewHeight * 0.505f,
            )
            housePath.lineTo(viewWidth * 0.305f, viewHeight * 0.505f)
            housePath.lineTo(viewWidth * 0.305f, viewHeight * 0.735f)
            housePath.quadTo(
                viewWidth * 0.305f,
                viewHeight * 0.755f,
                viewWidth * 0.325f,
                viewHeight * 0.755f,
            )
            housePath.lineTo(viewWidth * 0.675f, viewHeight * 0.755f)
            housePath.quadTo(
                viewWidth * 0.695f,
                viewHeight * 0.755f,
                viewWidth * 0.695f,
                viewHeight * 0.735f,
            )
            housePath.lineTo(viewWidth * 0.695f, viewHeight * 0.505f)
            housePath.lineTo(viewWidth * 0.75f, viewHeight * 0.505f)
            housePath.quadTo(
                viewWidth * 0.785f,
                viewHeight * 0.505f,
                viewWidth * 0.765f,
                viewHeight * 0.485f,
            )
            housePath.close()
            housePath.addRect(
                viewWidth * 0.425f,
                viewHeight * 0.535f,
                viewWidth * 0.575f,
                viewHeight * 0.655f,
                Path.Direction.CW,
            )
            canvas.drawPath(housePath, paint)
        }
    }

    companion object {
        private const val TAG = "TripleUnlock"
        private const val MAX_PRESS_GAP_MS = 1_000L
        private const val AUTO_SCREEN_OFF_MS = 3_000L
        private const val DUPLICATE_DPAD_SIGNAL_MS = 120L
        private const val SWIPE_UNLOCK_DISTANCE_DP = 120
        @Volatile
        private var connectedInstance = WeakReference<UnlockAccessibilityService>(null)

        fun lockNow(): Boolean {
            val service = connectedService() ?: return false
            service.armLock()
            return true
        }

        private fun connectedService(): UnlockAccessibilityService? = connectedInstance.get()
    }
}
