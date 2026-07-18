package com.anubhav.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * The full diagnostic-test catalog (assets/testsData.json), used to populate the
 * Metrics / Health Library list. Parsed once and cached in memory.
 */
object CatalogRepository {
    private const val ASSET = "testsData.json"
    private val gson = Gson()

    @Volatile
    private var cache: List<CatalogItem>? = null

    data class CatalogItem(
        val name: String = "",
        val category: String = "",
        val price: Double = 0.0,
    )

    private data class RawTest(
        val name: String = "",
        val category: String = "",
        val price: Double = 0.0,
    )

    private data class RawCatalog(
        @SerializedName("tests") val tests: List<RawTest> = emptyList(),
    )

    fun all(context: Context): List<CatalogItem> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val parsed = runCatching {
                context.applicationContext.assets.open(ASSET)
                    .use { it.readBytes().toString(Charsets.UTF_8) }
            }.mapCatching { gson.fromJson(it, RawCatalog::class.java) }
                .getOrNull()
            val items = parsed?.tests
                ?.filter { it.name.isNotBlank() }
                ?.map { CatalogItem(it.name.trim(), it.category.trim(), it.price) }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()
            cache = items
            return items
        }
    }

    fun search(context: Context, query: String): List<CatalogItem> {
        val all = all(context)
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all
        return all.filter {
            it.name.lowercase().contains(q) || it.category.lowercase().contains(q)
        }
    }
}
