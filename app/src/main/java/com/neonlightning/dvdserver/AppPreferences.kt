package com.neonlightning.dvdserver

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "dvd_server"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_THEME = "theme"
        private const val KEY_SORT_ORDER = "sort_order"
        private const val KEY_AUTOPLAY = "autoplay"
        private const val KEY_LAST_SELECTED_DVD = "last_selected_dvd"
        private const val KEY_SCREENSAVER_MINUTES = "screensaver_minutes"
        private const val KEY_CACHE_MODE = "cache_mode"

        private const val DEFAULT_BASE_URL = "http://192.168.1.100:4251"
        private const val DEFAULT_THEME = "Android TV"
        private const val DEFAULT_SORT_ORDER = 0
        private const val DEFAULT_AUTOPLAY = false
        private const val DEFAULT_LAST_SELECTED_DVD = 0
        private const val DEFAULT_SCREENSAVER_MINUTES = 2
        private const val DEFAULT_CACHE_MODE = 0
    }

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) = prefs.edit().putString(KEY_BASE_URL, value).apply()

    var theme: String
        get() = prefs.getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    var sortOrder: Int
        get() = prefs.getInt(KEY_SORT_ORDER, DEFAULT_SORT_ORDER)
        set(value) = prefs.edit().putInt(KEY_SORT_ORDER, value).apply()

    var isAutoplay: Boolean
        get() = prefs.getBoolean(KEY_AUTOPLAY, DEFAULT_AUTOPLAY)
        set(value) = prefs.edit().putBoolean(KEY_AUTOPLAY, value).apply()

    var lastSelectedDvdPosition: Int
        get() = prefs.getInt(KEY_LAST_SELECTED_DVD, DEFAULT_LAST_SELECTED_DVD)
        set(value) = prefs.edit().putInt(KEY_LAST_SELECTED_DVD, value).apply()

    var screensaverTimeoutMinutes: Int
        get() = prefs.getInt(KEY_SCREENSAVER_MINUTES, DEFAULT_SCREENSAVER_MINUTES)
        set(value) = prefs.edit().putInt(KEY_SCREENSAVER_MINUTES, value).apply()

    var cacheMode: Int
        get() = prefs.getInt(KEY_CACHE_MODE, DEFAULT_CACHE_MODE)
        set(value) = prefs.edit().putInt(KEY_CACHE_MODE, value).apply()
}
