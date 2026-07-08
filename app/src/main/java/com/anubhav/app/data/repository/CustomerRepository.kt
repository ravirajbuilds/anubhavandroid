package com.anubhav.app.data.repository

import com.anubhav.app.data.model.CustomerBill
import com.anubhav.app.data.model.CustomerPaymentRequest
import com.anubhav.app.data.model.CustomerPaymentResponse
import com.anubhav.app.data.model.CustomerPrebookRequest
import com.anubhav.app.data.model.CustomerPrebookResponse
import com.anubhav.app.data.model.CustomerProfile
import com.anubhav.app.data.model.CustomerReport
import com.anubhav.app.data.model.PrebookCalendar
import com.anubhav.app.data.remote.AktivApiClient

class CustomerRepository(
    private val api: com.anubhav.app.data.remote.CustomerApi = AktivApiClient.customerApi,
) {
    suspend fun getProfile(phone: String?, email: String?): Result<CustomerProfile> = runCatching {
        api.getProfile(phone = phone, email = email)
    }

    suspend fun getBills(phone: String): Result<List<CustomerBill>> = runCatching {
        api.getBills(phone)
    }

    suspend fun getReports(phone: String): Result<List<CustomerReport>> = runCatching {
        api.getReports(phone)
    }

    suspend fun getPendingPayments(phone: String): Result<List<CustomerBill>> = runCatching {
        api.getPendingPayments(phone)
    }

    suspend fun getPrebookCalendar(): Result<PrebookCalendar> = runCatching {
        api.getPrebookCalendar()
    }

    suspend fun createPrebook(request: CustomerPrebookRequest): Result<CustomerPrebookResponse> =
        runCatching {
            api.createPrebook(request)
        }

    suspend fun payPending(request: CustomerPaymentRequest): Result<CustomerPaymentResponse> =
        runCatching {
            api.payPending(request)
        }
}
