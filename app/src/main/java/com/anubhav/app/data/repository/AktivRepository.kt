package com.anubhav.app.data.repository

import android.content.Context
import com.anubhav.app.data.model.AktivBillNumber
import com.anubhav.app.data.model.AktivBookingRequest
import com.anubhav.app.data.model.AktivBookingResponse
import com.anubhav.app.data.model.AktivCollectionCentre
import com.anubhav.app.data.model.AktivDoctor
import com.anubhav.app.data.model.AktivLoginRequest
import com.anubhav.app.data.model.AktivLoginResponse
import com.anubhav.app.data.model.AktivReceptionUser
import com.anubhav.app.data.model.AktivTest
import com.anubhav.app.data.remote.AktivApiClient
import com.anubhav.app.utils.AktivDataCache
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AktivRepository(
    private val api: com.anubhav.app.data.remote.AktivApi = AktivApiClient.api,
) {
    suspend fun login(userid: String, password: String): Result<AktivLoginResponse> =
        runCatching { api.login(AktivLoginRequest(userid, password)) }

    suspend fun listReceptionUsers(): Result<List<AktivReceptionUser>> =
        runCatching { api.listUsers() }

    suspend fun searchTests(query: String): Result<List<AktivTest>> =
        runCatching { api.searchTests(query = query, limit = 50) }

    suspend fun searchTestsCached(context: Context, query: String): Result<List<AktivTest>> =
        runCatching {
            val trimmed = query.trim()
            val cacheKey = "tests_$trimmed"
            AktivDataCache.readTests(context, cacheKey)?.let { return@runCatching it }
            runCatching { api.searchTests(query = trimmed, limit = 50) }
                .onSuccess {
                    AktivDataCache.writeTests(context, cacheKey, it)
                    if (trimmed.isEmpty()) AktivDataCache.writeTests(context, "tests_catalog", it)
                }
                .getOrElse { error ->
                    filterCachedCatalog(context, trimmed).takeIf { it.isNotEmpty() } ?: throw error
                }
        }

    suspend fun refreshTests(context: Context): Result<List<AktivTest>> =
        runCatching {
            api.searchTests(query = "", limit = 500).also {
                AktivDataCache.writeTests(context, "tests_", it)
                AktivDataCache.writeTests(context, "tests_catalog", it)
            }
        }

    suspend fun searchDoctors(query: String): Result<List<AktivDoctor>> =
        runCatching { api.searchDoctors(query = query, limit = 50) }

    suspend fun listCollectionCentres(query: String = ""): Result<List<AktivCollectionCentre>> =
        runCatching { api.listCollectionCentres(query = query) }

    suspend fun nextBillNumber(
        billDate: LocalDate? = null,
        testMode: Boolean? = null,
    ): Result<AktivBillNumber> =
        runCatching {
            api.nextBillNumber(
                billDate = billDate?.format(DateTimeFormatter.ISO_LOCAL_DATE),
                testMode = testMode,
            )
        }

    suspend fun pushBooking(request: AktivBookingRequest): Result<AktivBookingResponse> =
        runCatching { api.pushBooking(request) }

    suspend fun pushBookingToAktiv(
        patientName: String,
        phone: String,
        sex: String,
        ageYear: Int?,
        ageMonth: Int?,
        ageDay: Int?,
        refrdoctorKey: Int?,
        collcentreKey: Int,
        testKeys: List<Int>,
        billDate: LocalDate?,
        billNumber: String?,
        amountPaid: Double?,
        receiptMode: String,
        chequeNo: String?,
        remarks: String?,
        testMode: Boolean?,
        sysUserKey: Int?,
    ): Result<AktivBookingResponse> =
        pushBooking(
            AktivBookingRequest(
                patientName = patientName,
                phone = phone,
                sex = sex,
                ageYear = ageYear,
                ageMonth = ageMonth,
                ageDay = ageDay,
                refrdoctorKey = refrdoctorKey,
                collcentreKey = collcentreKey,
                testKeys = testKeys,
                billDate = billDate?.format(DateTimeFormatter.ISO_LOCAL_DATE),
                billNumber = billNumber,
                amountPaid = amountPaid,
                receiptMode = receiptMode,
                chequeNo = chequeNo,
                remarks = remarks,
                testMode = testMode,
                sysUserKey = sysUserKey,
            ),
        )

    suspend fun cancelBooking(billKey: Int): Result<Boolean> =
        runCatching {
            api.cancelBooking(billKey)
            true
        }

    private fun filterCachedCatalog(context: Context, query: String): List<AktivTest> {
        val catalog = AktivDataCache.readTests(context, "tests_catalog")
            ?: AktivDataCache.readTests(context, "tests_")
            ?: emptyList()
        if (query.isBlank()) return catalog
        val needle = query.lowercase()
        return catalog.filter {
            it.testName.lowercase().contains(needle) ||
                it.testCode.lowercase().contains(needle) ||
                it.categoryName.orEmpty().lowercase().contains(needle)
        }
    }
}
