package com.anubhav.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anubhav.app.data.local.AppDatabase
import com.anubhav.app.data.repository.CatalogRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The catalog moved out of an in-memory list parsed from a bundled asset and into
 * SQLite, so these cover the parts that used to be plain Kotlin filtering: the seed
 * import, the indexed LIKE search, and wildcard escaping.
 */
@RunWith(RobolectricTestRunner::class)
// Robolectric 4.12 has no image for SDK 36; the app supports 24+ so any modern
// level exercises the same code.
@Config(sdk = [34])
class CatalogCacheTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        AppDatabase.closeForTests()
    }

    @Test
    fun seedsFromTheBundledAssetOnFirstUse() = runTest {
        val all = CatalogRepository.search(context, "")
        assertTrue("expected the seed catalog to load", all.isNotEmpty())
        assertTrue("seed should carry real prices", all.any { it.price > 0 })
    }

    @Test
    fun searchMatchesNameCaseInsensitively() = runTest {
        val hits = CatalogRepository.search(context, "lipid profile")
        assertTrue(hits.any { it.name.equals("LIPID PROFILE", ignoreCase = true) })
    }

    @Test
    fun searchAlsoMatchesCategory() = runTest {
        val hits = CatalogRepository.search(context, "pathology")
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.all { it.name.contains("pathology", true) || it.category.contains("pathology", true) })
    }

    @Test
    fun resultsAreCappedButTheCountReportsTheTruth() = runTest {
        val shown = CatalogRepository.search(context, "")
        val total = CatalogRepository.matchCount(context, "")

        assertTrue(shown.size <= CatalogRepository.SEARCH_LIMIT)
        assertTrue("the seed has more tests than one screenful", total >= shown.size)
    }

    @Test
    fun likeWildcardsInTheQueryAreEscaped() = runTest {
        // Unescaped, "%" would match every row — the classic "search box returns
        // everything" bug. It must behave as the literal character.
        val everything = CatalogRepository.matchCount(context, "")
        val percent = CatalogRepository.matchCount(context, "%")
        val underscore = CatalogRepository.matchCount(context, "_")

        assertTrue("'%' must not match the whole catalog", percent < everything)
        assertTrue("'_' must not match the whole catalog", underscore < everything)
    }

    @Test
    fun blankQueryReturnsTheWholeCatalogOrderedByName() = runTest {
        val all = CatalogRepository.search(context, "")
        val names = all.map { it.name.lowercase() }
        assertEquals(names.sorted(), names)
    }

    @Test
    fun findByNameIsExactAndCaseInsensitive() = runTest {
        val found = CatalogRepository.findByName(context, "lipid profile")
        assertNotNull("home screen prices depend on this lookup", found)
        assertEquals("LIPID PROFILE", found?.name?.uppercase())

        assertNull(CatalogRepository.findByName(context, "a test that does not exist"))
    }

    @Test
    fun theThreeHomeScreenTestsExistInTheCatalog() = runTest {
        // The Popular Tests cards look these up by exact name; a rename upstream would
        // silently blank their prices, so fail here instead.
        listOf("CBC (Complete Blood Count)", "LIPID PROFILE", "T3 T4 TSH").forEach { name ->
            assertNotNull("home screen references a missing test: $name",
                CatalogRepository.findByName(context, name))
        }
    }
}
