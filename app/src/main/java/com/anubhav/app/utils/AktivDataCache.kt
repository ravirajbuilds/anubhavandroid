package com.anubhav.app.utils

import android.content.Context
import com.anubhav.app.data.model.AktivTest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object AktivDataCache {
    private const val DIR = "aktiv_cache"
    private val gson = Gson()
    private val testListType = object : TypeToken<List<AktivTest>>() {}.type

    fun readTests(context: Context, key: String): List<AktivTest>? {
        val file = cacheFile(context, key)
        if (!file.exists()) return null
        return runCatching {
            gson.fromJson<List<AktivTest>>(file.readText(), testListType)
        }.getOrNull()
    }

    fun writeTests(context: Context, key: String, tests: List<AktivTest>) {
        val file = cacheFile(context, key)
        file.parentFile?.mkdirs()
        file.writeText(gson.toJson(tests))
    }

    private fun cacheFile(context: Context, key: String): File {
        val safeKey = key.lowercase().replace(Regex("[^a-z0-9_-]"), "_")
        return File(File(context.filesDir, DIR), "$safeKey.json")
    }
}
