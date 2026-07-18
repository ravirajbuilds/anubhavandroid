package com.anubhav.app.data.repository

import android.content.Context
import com.anubhav.app.data.model.TestInfo
import com.anubhav.app.data.model.TestInfoDb
import com.anubhav.app.data.model.TestInfoGeneric
import com.anubhav.app.data.model.TestInfoRange
import com.anubhav.app.data.model.TestInfoStage
import com.google.gson.Gson

/**
 * Loads and resolves educational test content from assets/testInfo.json.
 * Resolution: exact (normalised) test-name match for specific content, plus a
 * per-category generic fallback so EVERY catalog test shows something useful.
 */
object TestInfoRepository {
    private const val ASSET = "testInfo.json"
    private val gson = Gson()

    @Volatile
    private var db: TestInfoDb? = null
    private var byKey: Map<String, TestInfo> = emptyMap()

    private fun ensureLoaded(context: Context): TestInfoDb {
        db?.let { return it }
        synchronized(this) {
            db?.let { return it }
            val parsed = runCatching {
                context.applicationContext.assets.open(ASSET)
                    .use { it.readBytes().toString(Charsets.UTF_8) }
            }.mapCatching { gson.fromJson(it, TestInfoDb::class.java) }
                .getOrNull() ?: TestInfoDb()
            byKey = parsed.tests
                .filter { it.key.isNotBlank() }
                .associateBy { normalize(it.key) }
            db = parsed
            return parsed
        }
    }

    fun resolve(context: Context, testName: String, category: String?): Resolved {
        val data = ensureLoaded(context)
        val specific = byKey[normalize(testName)]
        val catKey = category?.takeIf { it.isNotBlank() } ?: specific?.category
        val generic = catKey?.let { data.generic[it] } ?: data.generic["default"]
        return Resolved(specific, generic, testName, catKey.orEmpty())
    }

    fun normalize(s: String): String = s.uppercase().replace(WHITESPACE, " ").trim()

    private val WHITESPACE = Regex("\\s+")

    data class Resolved(
        val specific: TestInfo?,
        val generic: TestInfoGeneric?,
        val testName: String,
        val category: String,
    ) {
        val hasGauge: Boolean
            get() = specific?.numeric == true && specific.ranges.isNotEmpty()

        /** Distinct sexes present in the ranges (for the Male/Female selector). */
        fun sexes(): List<String> {
            val s = specific ?: return emptyList()
            val list = s.ranges.map { it.sex }.distinct()
            return if (list.size <= 1 || list.all { it == "any" }) emptyList() else list
        }

        fun rangeFor(sex: String?): TestInfoRange? {
            val s = specific ?: return null
            if (s.ranges.isEmpty()) return null
            return s.ranges.firstOrNull { it.sex.equals(sex, ignoreCase = true) }
                ?: s.ranges.firstOrNull { it.sex == "any" }
                ?: s.ranges.first()
        }

        /** Stage id ("low"/"normal"/"high") for a value against the chosen range. */
        fun stageIdFor(value: Double, range: TestInfoRange?): String {
            val r = range ?: return "normal"
            return when {
                value < r.low -> "low"
                value > r.high -> "high"
                else -> "normal"
            }
        }

        fun stage(id: String): TestInfoStage? =
            specific?.stages?.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}
