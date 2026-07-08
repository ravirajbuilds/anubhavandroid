package com.anubhav.app.utils

import android.content.Context
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment

fun Context.localized(@StringRes englishRes: Int): String {
    val lm = LanguageManager(this)
    if (!lm.isBengali()) return getString(englishRes)
    val bnName = "${resources.getResourceEntryName(englishRes)}_bn"
    val bnId = resources.getIdentifier(bnName, "string", packageName)
    return if (bnId != 0) getString(bnId) else getString(englishRes)
}

fun Context.localized(@StringRes englishRes: Int, vararg args: Any): String {
    val lm = LanguageManager(this)
    if (!lm.isBengali()) return getString(englishRes, *args)
    val bnName = "${resources.getResourceEntryName(englishRes)}_bn"
    val bnId = resources.getIdentifier(bnName, "string", packageName)
    return if (bnId != 0) getString(bnId, *args) else getString(englishRes, *args)
}

fun Fragment.localized(@StringRes englishRes: Int): String = requireContext().localized(englishRes)

fun Fragment.localized(@StringRes englishRes: Int, vararg args: Any): String =
    requireContext().localized(englishRes, *args)
