package com.jarman.triplepressunlock

import android.content.Context

internal data class LockAppearance(
    val backgroundImageUri: String?,
    val iconColor: Int,
    val textColor: Int,
)

internal object LockAppearanceSettings {
    const val DEFAULT_BACKGROUND_COLOR = 0xFF1D1D1D.toInt()
    const val DEFAULT_ICON_COLOR = 0xFFE8E8E8.toInt()
    const val DEFAULT_TEXT_COLOR = 0xFFE8E8E8.toInt()

    private const val PREFERENCES_NAME = "lock_appearance"
    private const val KEY_BACKGROUND_IMAGE_URI = "background_image_uri"
    private const val KEY_ICON_COLOR = "icon_color"
    private const val KEY_TEXT_COLOR = "text_color"

    fun load(context: Context): LockAppearance {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return LockAppearance(
            backgroundImageUri = preferences.getString(KEY_BACKGROUND_IMAGE_URI, null),
            iconColor = preferences.getInt(KEY_ICON_COLOR, DEFAULT_ICON_COLOR),
            textColor = preferences.getInt(KEY_TEXT_COLOR, DEFAULT_TEXT_COLOR),
        )
    }

    fun setBackgroundImageUri(context: Context, uri: String?) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply {
                if (uri == null) remove(KEY_BACKGROUND_IMAGE_URI) else putString(KEY_BACKGROUND_IMAGE_URI, uri)
            }
            .apply()
    }

    fun setIconColor(context: Context, color: Int) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_ICON_COLOR, color)
            .apply()
    }

    fun setTextColor(context: Context, color: Int) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_TEXT_COLOR, color)
            .apply()
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
