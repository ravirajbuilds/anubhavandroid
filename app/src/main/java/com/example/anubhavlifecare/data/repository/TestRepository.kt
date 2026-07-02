package com.example.anubhavlifecare.data.repository

import com.example.anubhavlifecare.data.model.AktivTest
import com.example.anubhavlifecare.data.model.Test

class TestRepository(
    private val aktivRepository: AktivRepository = AktivRepository(),
) {
    suspend fun getAllTests(): Result<List<Test>> = aktivRepository.searchTests("")
        .map { tests -> tests.map { it.toAppTest() } }

    suspend fun searchTests(query: String): Result<List<Test>> =
        aktivRepository.searchTests(query).map { tests -> tests.map { it.toAppTest() } }

    suspend fun getPopularTests(): Result<List<Test>> = getAllTests()
        .map { tests -> tests.filter { it.isPopular }.ifEmpty { tests.take(5) } }

    suspend fun getTestsByCategory(category: String): Result<List<Test>> =
        getAllTests().map { tests -> tests.filter { it.category.equals(category, ignoreCase = true) } }

    private fun AktivTest.toAppTest(): Test = Test(
        id = testKey.toString(),
        name = testName,
        description = categoryName.orEmpty(),
        price = rate,
        originalPrice = rate,
        category = categoryName.orEmpty(),
        preparationInstructions = "",
        reportDeliveryTime = "Same day",
        isPopular = false,
        isActive = true,
    )
}
