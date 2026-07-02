package com.example.anubhavlifecare.data.repository

import com.example.anubhavlifecare.data.model.CustomerBill
import com.example.anubhavlifecare.data.model.CustomerPaymentRequest
import com.example.anubhavlifecare.data.model.CustomerPaymentResponse
import com.example.anubhavlifecare.data.model.CustomerPrebookRequest
import com.example.anubhavlifecare.data.model.CustomerPrebookResponse
import com.example.anubhavlifecare.data.model.CustomerProfile
import com.example.anubhavlifecare.data.model.CustomerReport
import com.example.anubhavlifecare.data.model.PrebookCalendar
import com.example.anubhavlifecare.data.remote.AktivApiClient

class CustomerRepository(
    private val api: com.example.anubhavlifecare.data.remote.CustomerApi = AktivApiClient.customerApi,
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
