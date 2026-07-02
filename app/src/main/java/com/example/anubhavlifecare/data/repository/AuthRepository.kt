package com.example.anubhavlifecare.data.repository

import com.example.anubhavlifecare.data.model.User

class AuthRepository {
    private var currentUser: User? = null

    suspend fun loginWithEmail(email: String, password: String): Result<User> {
        // Placeholder implementation - will integrate with Supabase later
        return try {
            // Simulate authentication
            val user = User(
                id = "temp_id",
                name = "User Name",
                email = email,
                phone = "",
                createdAt = System.currentTimeMillis().toString(),
                updatedAt = System.currentTimeMillis().toString()
            )
            currentUser = user
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginWithPhone(phone: String, otp: String): Result<User> {
        // Placeholder implementation - will integrate with Supabase later
        return try {
            val user = User(
                id = "temp_id",
                name = "User Name",
                email = "",
                phone = phone,
                createdAt = System.currentTimeMillis().toString(),
                updatedAt = System.currentTimeMillis().toString()
            )
            currentUser = user
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signUpWithEmail(
        name: String,
        email: String,
        phone: String,
        password: String
    ): Result<User> {
        return try {
            val user = User(
                id = "temp_id",
                name = name,
                email = email,
                phone = phone,
                createdAt = System.currentTimeMillis().toString(),
                updatedAt = System.currentTimeMillis().toString()
            )
            currentUser = user
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCurrentUser(): User? = currentUser

    fun isLoggedIn(): Boolean = currentUser != null

    fun logout() {
        currentUser = null
    }
}