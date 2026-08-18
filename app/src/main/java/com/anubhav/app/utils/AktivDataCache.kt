package com.anubhav.app.utils

import android.content.Context
import com.anubhav.app.data.model.AktivTest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object AktivDataCache {
    private const val DIR = "aktiv_cache"

    /**
     * Test rates change at the clinic, and the customer pays a 50% advance computed from
     * whatever this cache holds. An entry older than this is refused for pricing so the
     * app never charges against a price the server has since moved away from — the
     * booking would be rejected *after* the money was taken.
     */
    const val PRICE_MAX_AGE_MS = 12L * 60L * 60L * 1000L

    private val gson = Gson()
    private val testListType = object : TypeToken<List<AktivTest>>() {}.type

    /**
     * @param maxAgeMs when positive, entries older than this are treated as absent.
     *   Pass 0 to accept any age (the offline fallback: stale beats nothing).
     */
    fun readTests(context: Context, key: String, maxAgeMs: Long = 0L): List<AktivTest>? {
        val file = cacheFile(context, key)
        if (!file.exists()) return null
        if (maxAgeMs > 0L && System.currentTimeMillis() - file.lastModified() > maxAgeMs) return null
        return runCatching {
            gson.fromJson<List<AktivTest>>(file.readText(), testListType)
        }.getOrNull()
    }

    fun writeTests(context: Context, key: String, tests: List<AktivTest>) {
        val file = cacheFile(context, key)
        file.parentFile?.mkdirs()
        file.writeText(gson.toJson(tests))
    }

    /**
     * Delete the per-query files older builds wrote (`tests_cbc.json`, `tests_usg.json`, …).
     * Installs that have been through a lot of searching are carrying hundreds of them.
     */
    fun pruneLegacyQueryCaches(context: Context) {
        runCatching {
            File(context.filesDir, DIR).listFiles()?.forEach { file ->
                if (file.name != "tests_catalog.json") file.delete()
            }
        }
    }

    private fun cacheFile(context: Context, key: String): File {
        val safeKey = key.lowercase().replace(Regex("[^a-z0-9_-]"), "_")
        return File(File(context.filesDir, DIR), "$safeKey.json")
    }
}
