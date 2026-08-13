package com.jarman.triplepressunlock

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var statusView: TextView
    private lateinit var backgroundAccessButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.app_name)
        window.statusBarColor = Color.parseColor("#F5F7FB")
        window.navigationBarColor = Color.parseColor("#F5F7FB")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        setContentView(createContentView())
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun createContentView(): View {
        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.parseColor("#F5F7FB"))
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(32))
        }
        scrollView.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        content.addView(text(getString(R.string.screen_eyebrow), 13, Color.parseColor("#4C7DFF")))

        val titleView = text(getString(R.string.screen_title), 30, Color.parseColor("#111827"))
        titleView.setTypeface(titleView.typeface, Typeface.BOLD)
        content.addView(titleView, topMargin(6))

        val summary = text(
            getString(R.string.screen_summary),
            16,
            Color.parseColor("#4B5563"),
        ).apply {
            setLineSpacing(0f, 1.2f)
        }
        content.addView(summary, topMargin(10))

        val statusCard = card()
        statusView = text("", 17, Color.DKGRAY).apply {
            setTypeface(typeface, Typeface.BOLD)
        }
        statusCard.addView(statusView)
        statusCard.addView(
            text(getString(R.string.supported_keys), 14, Color.parseColor("#4B5563")),
            topMargin(8),
        )
        content.addView(statusCard, topMargin(24))

        val accessibilityButton = primaryButton(getString(R.string.open_accessibility_settings))
        accessibilityButton.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (_: RuntimeException) {
                toast(R.string.accessibility_settings_error)
            }
        }
        content.addView(accessibilityButton, topMargin(18))

        backgroundAccessButton = secondaryButton(getString(R.string.request_background_access))
        backgroundAccessButton.setOnClickListener {
            requestBackgroundAccess()
        }
        content.addView(backgroundAccessButton, topMargin(10))

        val testButton = secondaryButton(getString(R.string.test_lock_overlay))
        testButton.setOnClickListener {
            if (!UnlockAccessibilityService.lockNow()) {
                toast(R.string.accessibility_required)
            }
        }
        content.addView(testButton, topMargin(10))

        val securityButton = textButton(getString(R.string.open_security_settings))
        securityButton.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
            } catch (_: RuntimeException) {
                toast(R.string.security_settings_error)
            }
        }
        content.addView(securityButton, topMargin(8))

        val stepsTitle = text(getString(R.string.setup_steps_title), 19, Color.parseColor("#111827"))
        stepsTitle.setTypeface(stepsTitle.typeface, Typeface.BOLD)
        content.addView(stepsTitle, topMargin(28))

        val steps = text(
            getString(R.string.setup_steps),
            15,
            Color.parseColor("#374151"),
        ).apply {
            setLineSpacing(dp(5).toFloat(), 1.15f)
        }
        content.addView(steps, topMargin(10))

        val safety = text(
            getString(R.string.touch_unlock_and_disclaimer),
            13,
            Color.parseColor("#6B7280"),
        ).apply {
            setLineSpacing(0f, 1.2f)
        }
        content.addView(safety, topMargin(24))
        return scrollView
    }

    private fun refreshState() {
        val enabled = isAccessibilityServiceEnabled()
        statusView.setText(if (enabled) R.string.service_enabled else R.string.service_disabled)
        statusView.setTextColor(Color.parseColor(if (enabled) "#15803D" else "#B45309"))
        backgroundAccessButton.setText(
            if (isIgnoringBatteryOptimizations()) {
                R.string.background_access_granted
            } else {
                R.string.request_background_access
            },
        )
    }

    @SuppressLint("BatteryLife")
    private fun requestBackgroundAccess() {
        if (isIgnoringBatteryOptimizations()) {
            toast(R.string.background_access_already_granted)
            return
        }

        val directRequest = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        try {
            startActivity(directRequest)
        } catch (_: RuntimeException) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: RuntimeException) {
                toast(R.string.background_access_settings_error)
            }
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val expectedClass = UnlockAccessibilityService::class.java.name
        return manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
                packageName == serviceInfo.packageName && expectedClass == serviceInfo.name
            }
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), Color.parseColor("#E5E7EB"))
        }
        elevation = dp(2).toFloat()
    }

    private fun primaryButton(label: String): Button = baseButton(label).apply {
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#4C7DFF"))
            cornerRadius = dp(14).toFloat()
        }
    }

    private fun secondaryButton(label: String): Button = baseButton(label).apply {
        setTextColor(Color.parseColor("#244AA5"))
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), Color.parseColor("#B8C8F5"))
        }
    }

    private fun textButton(label: String): Button = baseButton(label).apply {
        setTextColor(Color.parseColor("#4B5563"))
        setBackgroundColor(Color.TRANSPARENT)
    }

    private fun baseButton(label: String): Button = Button(this).apply {
        text = label
        textSize = 16f
        isAllCaps = false
        gravity = Gravity.CENTER
        minHeight = dp(52)
        setPadding(dp(16), dp(12), dp(16), dp(12))
    }

    private fun text(value: CharSequence, sizeSp: Int, color: Int): TextView = TextView(this).apply {
        text = value
        textSize = sizeSp.toFloat()
        setTextColor(color)
    }

    private fun topMargin(marginDp: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(marginDp)
        }

    private fun toast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
