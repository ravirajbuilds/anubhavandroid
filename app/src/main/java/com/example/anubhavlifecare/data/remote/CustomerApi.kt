package com.example.anubhavlifecare.data.remote

import com.example.anubhavlifecare.data.model.CustomerBill
import com.example.anubhavlifecare.data.model.CustomerPaymentRequest
import com.example.anubhavlifecare.data.model.CustomerPaymentResponse
import com.example.anubhavlifecare.data.model.CustomerPrebookRequest
import com.example.anubhavlifecare.data.model.CustomerPrebookResponse
import com.example.anubhavlifecare.data.model.CustomerProfile
import com.example.anubhavlifecare.data.model.CustomerReport
import com.example.anubhavlifecare.data.model.PrebookCalendar
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface CustomerApi {
    @GET("api/customer/profile")
    suspend fun getProfile(
        @Query("phone") phone: String? = null,
        @Query("email") email: String? = null,
    ): CustomerProfile

    @GET("api/customer/bills")
    suspend fun getBills(
        @Query("phone") phone: String,
        @Query("limit") limit: Int = 50,
    ): List<CustomerBill>

    @GET("api/customer/reports")
    suspend fun getReports(
        @Query("phone") phone: String,
        @Query("limit") limit: Int = 50,
    ): List<CustomerReport>

    @GET("api/customer/pending-payments")
    suspend fun getPendingPayments(@Query("phone") phone: String): List<CustomerBill>

    @GET("api/customer/prebook/calendar")
    suspend fun getPrebookCalendar(
        @Query("months_ahead") monthsAhead: Int = 3,
    ): PrebookCalendar

    @POST("api/customer/prebook")
    suspend fun createPrebook(@Body request: CustomerPrebookRequest): CustomerPrebookResponse

    @POST("api/customer/payments")
    suspend fun payPending(@Body request: CustomerPaymentRequest): CustomerPaymentResponse
}
