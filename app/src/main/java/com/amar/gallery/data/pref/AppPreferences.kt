package com.amar.gallery.data.pref

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("amar_prefs", Context.MODE_PRIVATE)

    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()

    var userPhone: String
        get() = prefs.getString(KEY_USER_PHONE, "+8801786925863") ?: "+8801786925863"
        set(value) = prefs.edit().putString(KEY_USER_PHONE, value).apply()

    var userName: String
        get() = prefs.getString(KEY_USER_NAME, "User") ?: "User"
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM"
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    var appLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_APP_LOCK, value).apply()

    var wifiOnlyEnabled: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()

    var selectedChannelId: Long
        get() = prefs.getLong(KEY_SELECTED_CHANNEL, 0L)
        set(value) = prefs.edit().putLong(KEY_SELECTED_CHANNEL, value).apply()

    var cacheLimitMb: Int
        get() = prefs.getInt(KEY_CACHE_LIMIT_MB, 3072)
        set(value) = prefs.edit().putInt(KEY_CACHE_LIMIT_MB, value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_APP_LOCK = "app_lock"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_SELECTED_CHANNEL = "selected_channel"
        private const val KEY_CACHE_LIMIT_MB = "cache_limit_mb"

        @Volatile
        private var instance: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences {
            return instance ?: synchronized(this) {
                instance ?: AppPreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}

