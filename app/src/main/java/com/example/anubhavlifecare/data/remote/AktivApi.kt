package com.example.anubhavlifecare.data.remote

import com.example.anubhavlifecare.data.model.AktivLoginRequest
import com.example.anubhavlifecare.data.model.AktivLoginResponse
import com.example.anubhavlifecare.data.model.AktivBillNumber
import com.example.anubhavlifecare.data.model.AktivBookingRequest
import com.example.anubhavlifecare.data.model.AktivBookingResponse
import com.example.anubhavlifecare.data.model.AktivCollectionCentre
import com.example.anubhavlifecare.data.model.AktivDoctor
import com.example.anubhavlifecare.data.model.AktivReceptionUser
import com.example.anubhavlifecare.data.model.AktivTest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AktivApi {
    @POST("api/auth/login")
    suspend fun login(@Body request: AktivLoginRequest): AktivLoginResponse

    @GET("api/users")
    suspend fun listUsers(): List<AktivReceptionUser>

    @GET("api/tests")
    suspend fun searchTests(
        @Query("q") query: String = "",
        @Query("limit") limit: Int = 50,
    ): List<AktivTest>

    @GET("api/doctors")
    suspend fun searchDoctors(
        @Query("q") query: String = "",
        @Query("limit") limit: Int = 50,
    ): List<AktivDoctor>

    @GET("api/collection-centres")
    suspend fun listCollectionCentres(
        @Query("q") query: String = "",
    ): List<AktivCollectionCentre>

    @GET("api/next-bill-number")
    suspend fun nextBillNumber(
        @Query("bill_date") billDate: String? = null,
        @Query("test_mode") testMode: Boolean? = null,
    ): AktivBillNumber

    @POST("api/bookings")
    suspend fun pushBooking(@Body request: AktivBookingRequest): AktivBookingResponse

    @POST("api/bookings/{billKey}/cancel")
    suspend fun cancelBooking(
        @Path("billKey") billKey: Int,
        @Body body: Map<String, Int?> = emptyMap(),
    ): Map<String, Any>
}
