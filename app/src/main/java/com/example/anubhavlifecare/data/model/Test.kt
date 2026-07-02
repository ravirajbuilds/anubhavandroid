package com.example.anubhavlifecare.data.model

data class Test(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val originalPrice: Double = 0.0,
    val category: String = "",
    val preparationInstructions: String = "",
    val reportDeliveryTime: String = "",
    val isPopular: Boolean = false,
    val isActive: Boolean = true
)