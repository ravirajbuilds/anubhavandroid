package com.anubhav.app.utils

import android.content.Context

object SessionManager {
    private const val PREFS_NAME = "aktiv_admin_session"
    private const val KEY_USER_KEY = "user_key"
    private const val KEY_USERID = "userid"
    private const val KEY_USERNAME = "username"

    fun save(context: Context, userKey: Int, userid: String, username: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_USER_KEY, userKey)
            .putString(KEY_USERID, userid)
            .putString(KEY_USERNAME, username)
            .apply()
    }

    fun isLoggedIn(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .contains(KEY_USER_KEY)

    fun getUserKey(context: Context): Int? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return if (prefs.contains(KEY_USER_KEY)) prefs.getInt(KEY_USER_KEY, -1) else null
    }

    fun getUserid(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USERID, null)

    fun getUsername(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USERNAME, null)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
