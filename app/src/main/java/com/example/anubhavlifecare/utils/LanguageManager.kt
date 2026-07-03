package com.example.anubhavlifecare.utils

import android.content.Context
import android.content.SharedPreferences

class LanguageManager(private val context: Context) {

    companion object {
        private const val PREF_NAME = "language_pref"
        private const val KEY_LANGUAGE = "selected_language"
        const val LANGUAGE_ENGLISH = "en"
        const val LANGUAGE_BENGALI = "bn"
    }

    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun hasSelectedLanguage(): Boolean =
        sharedPrefs.contains(KEY_LANGUAGE)

    fun getCurrentLanguage(): String {
        return sharedPrefs.getString(KEY_LANGUAGE, LANGUAGE_ENGLISH) ?: LANGUAGE_ENGLISH
    }

    fun setLanguage(language: String) {
        sharedPrefs.edit()
            .putString(KEY_LANGUAGE, language)
            .apply()
    }

    fun toggleLanguage(): String {
        val currentLang = getCurrentLanguage()
        val newLang = if (currentLang == LANGUAGE_ENGLISH) LANGUAGE_BENGALI else LANGUAGE_ENGLISH
        setLanguage(newLang)
        return newLang
    }

    fun isBengali(): Boolean {
        return getCurrentLanguage() == LANGUAGE_BENGALI
    }

    fun isEnglish(): Boolean {
        return getCurrentLanguage() == LANGUAGE_ENGLISH
    }

    // Helper method to get localized string
    fun getString(context: Context, englishStringRes: Int, bengaliStringRes: Int): String {
        return if (isBengali()) {
            try {
                context.getString(bengaliStringRes)
            } catch (e: Exception) {
                context.getString(englishStringRes) // Fallback to English
            }
        } else {
            context.getString(englishStringRes)
        }
    }
}