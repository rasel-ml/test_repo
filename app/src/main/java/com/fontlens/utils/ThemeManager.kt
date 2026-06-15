package com.fontlens.utils

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import androidx.appcompat.app.AppCompatDelegate
import com.fontlens.R
import com.fontlens.data.AppSettings
import com.fontlens.data.ColorTheme

/**
 * Applies the active color+mode theme by overriding the app's shared color
 * resources at runtime via a theme overlay on the Application context isn't
 * possible without custom attribute injection, so instead we expose helper
 * functions that return the correct color values for the active theme, and
 * each fragment/activity calls `ThemeManager.apply(context)` to repaint its
 * own root view via the normal attribute system.
 *
 * A simpler, reliable pattern: We define 8 full MaterialComponents themes in
 * themes.xml (one per color×mode combination) and switch the activity theme.
 */
object ThemeManager {

    /** Returns the theme style resource for the given settings. */
    fun themeResId(settings: AppSettings): Int {
        val dark = if (settings.followSystem) isSystemDark() else settings.darkMode
        return when (settings.colorTheme) {
            ColorTheme.GREEN  -> if (dark) R.style.Theme_FontLens_Green_Dark  else R.style.Theme_FontLens_Green_Light
            ColorTheme.BLUE   -> if (dark) R.style.Theme_FontLens_Blue_Dark   else R.style.Theme_FontLens_Blue_Light
            ColorTheme.RED    -> if (dark) R.style.Theme_FontLens_Red_Dark    else R.style.Theme_FontLens_Red_Light
            ColorTheme.YELLOW -> if (dark) R.style.Theme_FontLens_Yellow_Dark else R.style.Theme_FontLens_Yellow_Light
        }
    }

    /**
     * Sets the night-mode delegate so the system window chrome (status bar,
     * nav bar) follows the right mode. Call before setContentView.
     */
    fun applyNightMode(settings: AppSettings) {
        val dark = if (settings.followSystem) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            return
        } else settings.darkMode
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private fun isSystemDark(): Boolean =
        (android.content.res.Configuration.UI_MODE_NIGHT_YES ==
            (android.content.res.Resources.getSystem().configuration.uiMode
                and android.content.res.Configuration.UI_MODE_NIGHT_MASK))

    /** Bullet color for spinner rows — returns the accent hex of each theme. */
    fun accentColorForTheme(theme: ColorTheme, dark: Boolean): Int = when (theme) {
        ColorTheme.GREEN  -> if (dark) Color.parseColor("#66BB6A") else Color.parseColor("#2E7D32")
        ColorTheme.BLUE   -> if (dark) Color.parseColor("#5C9CE6") else Color.parseColor("#1565C0")
        ColorTheme.RED    -> if (dark) Color.parseColor("#EF5350") else Color.parseColor("#B71C1C")
        ColorTheme.YELLOW -> if (dark) Color.parseColor("#FFB300") else Color.parseColor("#E65100")
    }

    /** Representative bullet color (always light variant) for the spinner labels. */
    fun bulletColor(theme: ColorTheme): Int = accentColorForTheme(theme, false)
}
