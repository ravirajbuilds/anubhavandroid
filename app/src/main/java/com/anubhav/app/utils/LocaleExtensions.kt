package com.anubhav.app.utils

import android.content.Context
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import java.util.concurrent.ConcurrentHashMap

/**
 * Bengali strings live alongside the English ones under a `_bn` suffix rather than in a
 * `values-bn/` folder, because the language is a user setting inside the app rather than
 * the device locale.
 *
 * Resolving that suffix means a `getIdentifier` call, which looks a resource up by name
 * and is slow enough that Android's own docs warn against it. Screens here call
 * `localized()` a dozen or more times per bind, so the mapping is resolved once per
 * string and remembered — resource ids are fixed for the life of the process.
 */
private val bengaliIds = ConcurrentHashMap<Int, Int>()

private fun Context.bengaliIdFor(@StringRes englishRes: Int): Int =
    bengaliIds.getOrPut(englishRes) {
        val bnName = "${resources.getResourceEntryName(englishRes)}_bn"
        // 0 means "no Bengali version"; caching that is the point — an untranslated
        // string would otherwise pay for a failed lookup every single time.
        resources.getIdentifier(bnName, "string", packageName)
    }

fun Context.localized(@StringRes englishRes: Int): String {
    if (!LanguageManager(this).isBengali()) return getString(englishRes)
    val bnId = bengaliIdFor(englishRes)
    return if (bnId != 0) getString(bnId) else getString(englishRes)
}

fun Context.localized(@StringRes englishRes: Int, vararg args: Any): String {
    if (!LanguageManager(this).isBengali()) return getString(englishRes, *args)
    val bnId = bengaliIdFor(englishRes)
    return if (bnId != 0) getString(bnId, *args) else getString(englishRes, *args)
}

fun Fragment.localized(@StringRes englishRes: Int): String = requireContext().localized(englishRes)

fun Fragment.localized(@StringRes englishRes: Int, vararg args: Any): String =
    requireContext().localized(englishRes, *args)
