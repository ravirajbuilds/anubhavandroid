package com.anubhav.app.data.repository

import android.content.Context
import com.anubhav.app.data.model.CollectorPatient
import com.anubhav.app.data.model.CollectorPatientRequest
import com.anubhav.app.data.model.CustomerBill
import com.anubhav.app.data.model.CustomerPaymentRequest
import com.anubhav.app.data.model.CustomerPaymentResponse
import com.anubhav.app.data.model.CustomerPrebookRequest
import com.anubhav.app.data.model.CustomerPrebookResponse
import com.anubhav.app.data.model.CustomerProfile
import com.anubhav.app.data.model.CustomerReport
import com.anubhav.app.data.model.CustomerVerifyRequest
import com.anubhav.app.data.model.CustomerVerifyResponse
import com.anubhav.app.data.model.PrebookCalendar
import com.anubhav.app.data.remote.AktivApiClient
import com.anubhav.app.utils.ReportCache

class CustomerRepository(
    private val api: com.anubhav.app.data.remote.CustomerApi = AktivApiClient.customerApi,
) {
    /** Guest/patient 2-of-3 fuzzy login. Returns the matched patient + canonical phone. */
    suspend fun verify(
        name: String,
        phone: String,
        billNo: String = "",
        billDate: String? = null,
    ): Result<CustomerVerifyResponse> = runCatching {
        api.verify(CustomerVerifyRequest(name.trim(), phone.trim(), billNo.trim(), billDate))
    }

    /** All visits (bills) under a phone since 2022, from the static all-history DB. */
    suspend fun getHistory(phone: String): Result<com.anubhav.app.data.model.CustomerHistoryResponse> =
        runCatching { api.getHistory(phone.trim()) }

    suspend fun getProfile(phone: String?, email: String?): Result<CustomerProfile> =
        runCatching { api.getProfile(phone = phone, email = email) }

    suspend fun getBills(phone: String): Result<List<CustomerBill>> =
        runCatching { api.getBills(phone) }

    suspend fun getBillsCached(
        context: Context,
        phone: String,
        forceRefresh: Boolean = false,
    ): Result<List<CustomerBill>> = runCatching {
        val cacheKey = "bills_$phone"
        if (!forceRefresh) {
            ReportCache.readBills(context, cacheKey)?.let { return@runCatching it }
        }
        runCatching { api.getBills(phone) }
            .onSuccess { ReportCache.writeBills(context, cacheKey, it) }
            .getOrElse { error -> ReportCache.readBills(context, cacheKey) ?: throw error }
    }

    suspend fun getReports(phone: String): Result<List<CustomerReport>> =
        runCatching { api.getReports(phone) }

    suspend fun getReportsCached(
        context: Context,
        phone: String,
        forceRefresh: Boolean = false,
    ): Result<List<CustomerReport>> = runCatching {
        val cacheKey = "customer_$phone"
        if (!forceRefresh) {
            ReportCache.read(context, cacheKey)?.let { return@runCatching it }
        }
        runCatching { api.getReports(phone) }
            .onSuccess { ReportCache.write(context, cacheKey, it) }
            .getOrElse { error -> ReportCache.read(context, cacheKey) ?: throw error }
    }

    suspend fun getPendingPayments(phone: String): Result<List<CustomerBill>> =
        runCatching { api.getPendingPayments(phone) }

    suspend fun getPendingPaymentsCached(
        context: Context,
        phone: String,
        forceRefresh: Boolean = false,
    ): Result<List<CustomerBill>> = runCatching {
        val cacheKey = "pending_$phone"
        if (!forceRefresh) {
            ReportCache.readBills(context, cacheKey)?.let { return@runCatching it }
        }
        runCatching { api.getPendingPayments(phone) }
            .onSuccess { ReportCache.writeBills(context, cacheKey, it) }
            .getOrElse { error -> ReportCache.readBills(context, cacheKey) ?: throw error }
    }

    suspend fun payPending(request: CustomerPaymentRequest): Result<CustomerPaymentResponse> =
        runCatching { api.payPending(request) }

    suspend fun payPendingCached(
        context: Context,
        request: CustomerPaymentRequest,
    ): Result<CustomerPaymentResponse> =
        runCatching {
            api.payPending(request).also {
                if (it.success) ReportCache.removePendingBill(context, request.phone, request.billKey)
            }
        }

    suspend fun getPrebookCalendar(): Result<PrebookCalendar> =
        runCatching { api.getPrebookCalendar() }

    suspend fun createPrebook(request: CustomerPrebookRequest): Result<CustomerPrebookResponse> =
        runCatching { api.createPrebook(request) }

    suspend fun getCollectorPatients(collectorUserKey: Int): Result<List<CollectorPatient>> =
        runCatching { api.getCollectorPatients(collectorUserKey) }

    suspend fun getCollectorPatientsCached(
        context: Context,
        collectorUserKey: Int,
        forceRefresh: Boolean = false,
    ): Result<List<CollectorPatient>> = runCatching {
        val cacheKey = "collector_patients_$collectorUserKey"
        if (!forceRefresh) {
            ReportCache.readCollectorPatients(context, cacheKey)?.let { return@runCatching it }
        }
        runCatching { api.getCollectorPatients(collectorUserKey) }
            .map { serverPatients ->
                val localUnsynced = ReportCache.readCollectorPatients(context, cacheKey)
                    .orEmpty()
                    .filter { (it.id ?: 0) < 0 }
                mergeCollectorPatients(serverPatients + localUnsynced).also {
                    ReportCache.writeCollectorPatients(context, cacheKey, it)
                }
            }
            .getOrElse { error -> ReportCache.readCollectorPatients(context, cacheKey) ?: throw error }
    }

    suspend fun createCollectorPatient(request: CollectorPatientRequest): Result<CollectorPatient> =
        runCatching { api.createCollectorPatient(request) }

    suspend fun createCollectorPatientCached(
        context: Context,
        request: CollectorPatientRequest,
    ): Result<CollectorPatient> =
        runCatching {
            runCatching { api.createCollectorPatient(request) }
                .onSuccess { appendCollectorPatient(context, request.collectorUserKey, it) }
                .getOrElse {
                    val queueKey = "collector_patient_queue_${request.collectorUserKey}"
                    val queued = ReportCache.readQueuedCollectorPatients(context, queueKey)
                        .filterNot { it.logicalKey() == request.logicalKey() } + request
                    ReportCache.writeQueuedCollectorPatients(context, queueKey, queued)
                    val localId = -((System.currentTimeMillis() % Int.MAX_VALUE).toInt().coerceAtLeast(1))
                    CollectorPatient(
                        id = localId,
                        collectorUserKey = request.collectorUserKey,
                        patientName = request.patientName,
                        phone = request.phone,
                        ageYear = request.ageYear,
                    sex = request.sex,
                    referredBy = request.referredBy,
                    notes = request.notes,
                    followupStatus = request.followupStatus,
                    createdAt = "Saved on phone",
                ).also { appendCollectorPatient(context, request.collectorUserKey, it) }
                }
        }

    suspend fun syncQueuedCollectorPatients(context: Context, collectorUserKey: Int): Result<Int> =
        runCatching {
            val queueKey = "collector_patient_queue_$collectorUserKey"
            val queued = ReportCache.readQueuedCollectorPatients(context, queueKey)
            if (queued.isEmpty()) return@runCatching 0

            val remaining = mutableListOf<CollectorPatientRequest>()
            var synced = 0
            queued.forEach { request ->
                runCatching { api.createCollectorPatient(request) }
                    .onSuccess {
                        synced += 1
                        appendCollectorPatient(context, collectorUserKey, it)
                    }
                    .onFailure { remaining += request }
            }
            ReportCache.writeQueuedCollectorPatients(context, queueKey, remaining)
            synced
        }

    fun getQueuedCollectorPatientCount(context: Context, collectorUserKey: Int): Int =
        ReportCache.readQueuedCollectorPatients(context, "collector_patient_queue_$collectorUserKey").size

    suspend fun getCollectorReportsCached(
        context: Context,
        collectorUserKey: Int,
        forceRefresh: Boolean = false,
    ): Result<List<CustomerReport>> = runCatching {
        val cacheKey = "collector_$collectorUserKey"
        if (!forceRefresh) {
            ReportCache.read(context, cacheKey)?.let { return@runCatching it }
        }
        runCatching { api.getCollectorReports(collectorUserKey) }
            .onSuccess { ReportCache.write(context, cacheKey, it) }
            .getOrElse { error -> ReportCache.read(context, cacheKey) ?: throw error }
    }

    private fun appendCollectorPatient(context: Context, collectorUserKey: Int, patient: CollectorPatient) {
        val cacheKey = "collector_patients_$collectorUserKey"
        val existing = ReportCache.readCollectorPatients(context, cacheKey).orEmpty()
        val merged = mergeCollectorPatients(listOf(patient) + existing)
        ReportCache.writeCollectorPatients(context, cacheKey, merged)
    }

    private fun mergeCollectorPatients(patients: List<CollectorPatient>): List<CollectorPatient> =
        patients
            .sortedWith(
                compareByDescending<CollectorPatient> { (it.id ?: 0) > 0 }
                    .thenByDescending { it.id ?: 0 },
            )
            .distinctBy { it.logicalKey() }

    private fun CollectorPatient.logicalKey(): String =
        "${phone.filter { it.isDigit() }.takeLast(10)}|${patientName.trim().lowercase()}"

    private fun CollectorPatientRequest.logicalKey(): String =
        "${phone.filter { it.isDigit() }.takeLast(10)}|${patientName.trim().lowercase()}"
}
