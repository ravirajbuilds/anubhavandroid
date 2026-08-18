package com.anubhav.app.data.repository

import android.content.Context
import com.anubhav.app.data.local.AppDatabase
import com.anubhav.app.data.local.CatalogTestEntity
import com.anubhav.app.data.remote.AktivApiClient
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The diagnostic-test catalog.
 *
 * The server is the source of truth; the phone keeps a SQLite copy so searching
 * is a `LIKE` against an index instead of a linear scan over a list held in
 * memory, and so the screen still works with no network. A small seed file ships
 * in assets purely so the very first launch has something to show before the
 * first sync completes.
 */
object CatalogRepository {

    /** The LIKE escape character; must match the `ESCAPE` clause in [com.anubhav.app.data.local.CatalogDao]. */
    private val ESCAPE = "\\"

    private const val SEED_ASSET = "catalog_seed.json"
    private const val PREFS = "catalog_cache"
    private const val KEY_VERSION = "catalog_version"

    /** A search screen cannot usefully show more than this; the count line reports the real total. */
    const val SEARCH_LIMIT = 300

    private val gson = Gson()

    data class CatalogItem(
        val name: String,
        val category: String,
        val price: Double,
    )

    private data class SeedTest(
        @SerializedName("n") val name: String = "",
        @SerializedName("c") val category: String = "",
        @SerializedName("p") val price: Double = 0.0,
    )

    private data class SeedFile(
        @SerializedName("tests") val tests: List<SeedTest> = emptyList(),
    )

    /** Rows matching [query], capped at [SEARCH_LIMIT]. */
    suspend fun search(context: Context, query: String): List<CatalogItem> =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            ensureSeeded(app)
            val dao = AppDatabase.get(app).catalogDao()
            val rows = if (query.isBlank()) {
                dao.listAll(SEARCH_LIMIT)
            } else {
                dao.search(likePattern(query), SEARCH_LIMIT)
            }
            rows.map { CatalogItem(it.name, it.category, it.price) }
        }

    /** Total matches for [query], which may exceed what [search] returns. */
    suspend fun matchCount(context: Context, query: String): Int =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            ensureSeeded(app)
            val dao = AppDatabase.get(app).catalogDao()
            if (query.isBlank()) dao.count() else dao.countMatching(likePattern(query))
        }

    /** One test by its exact catalog name, or null when the cache has never heard of it. */
    suspend fun findByName(context: Context, name: String): CatalogItem? =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            ensureSeeded(app)
            AppDatabase.get(app).catalogDao().findByName(name.trim().lowercase())
                ?.let { CatalogItem(it.name, it.category, it.price) }
        }

    /**
     * Pull the catalog from the server if it changed. Safe to call on every visit
     * to the screen — an unchanged catalog costs one small request. Failures are
     * swallowed: the cached copy stays valid and the screen keeps working offline.
     */
    suspend fun sync(context: Context): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        ensureSeeded(app)
        runCatching {
            val known = prefs(app).getString(KEY_VERSION, null)
            val response = AktivApiClient.catalogApi.getCatalog(known)
            if (response.unchanged || response.tests.isEmpty()) return@runCatching false

            AppDatabase.get(app).catalogDao().replaceAll(
                response.tests
                    .filter { it.name.isNotBlank() }
                    .map { entity(it.name, it.category, it.price) },
            )
            prefs(app).edit().putString(KEY_VERSION, response.version).apply()
            true
        }.getOrDefault(false)
    }

    /**
     * Populate the table from the bundled seed the first time only. The seed carries
     * no server version, so the next [sync] still downloads the live catalog.
     */
    private suspend fun ensureSeeded(context: Context) {
        val dao = AppDatabase.get(context).catalogDao()
        if (dao.count() > 0) return
        val seed = runCatching {
            context.assets.open(SEED_ASSET).use { it.readBytes().toString(Charsets.UTF_8) }
        }.mapCatching { gson.fromJson(it, SeedFile::class.java) }
            .getOrNull() ?: return
        val rows = seed.tests
            .filter { it.name.isNotBlank() }
            .map { entity(it.name.trim(), it.category.trim(), it.price) }
        if (rows.isNotEmpty()) dao.insertAll(rows)
    }

    private fun entity(name: String, category: String, price: Double) = CatalogTestEntity(
        name = name,
        category = category,
        price = price,
        nameLower = name.lowercase(),
        categoryLower = category.lowercase(),
    )

    /**
     * `%`, `_` and `\` are LIKE wildcards; a user typing "25(OH)_D" must not match
     * everything. Escaped here to pair with the `ESCAPE '\'` in the queries.
     */
    private fun likePattern(query: String): String {
        val escaped = query.trim().lowercase()
            .replace(ESCAPE, ESCAPE + ESCAPE)
            .replace("%", ESCAPE + "%")
            .replace("_", ESCAPE + "_")
        return "%$escaped%"
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
