package com.anubhav.app.data.remote

import com.anubhav.app.data.model.CustomerBill
import com.anubhav.app.data.model.CustomerPaymentRequest
import com.anubhav.app.data.model.CustomerPaymentResponse
import com.anubhav.app.data.model.CustomerPrebookRequest
import com.anubhav.app.data.model.CustomerPrebookResponse
import com.anubhav.app.data.model.CustomerProfile
import com.anubhav.app.data.model.CustomerReport
import com.anubhav.app.data.model.PrebookCalendar
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
