package com.anubhav.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Server copy of the test catalog. The phone sends the version it already has,
 * so an unchanged catalog costs one small response instead of the whole list.
 */
interface CatalogApi {
    @GET("api/catalog")
    suspend fun getCatalog(
        @Query("known_version") knownVersion: String? = null,
    ): CatalogResponse
}

data class CatalogResponse(
    val version: String = "",
    val count: Int = 0,
    val unchanged: Boolean = false,
    val tests: List<CatalogTestDto> = emptyList(),
)

data class CatalogTestDto(
    val key: Int = 0,
    val name: String = "",
    val category: String = "",
    val price: Double = 0.0,
)
