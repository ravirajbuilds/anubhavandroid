package com.anubhav.app.data.repository

import com.anubhav.app.data.model.AktivLoginRequest
import com.anubhav.app.data.model.AktivLoginResponse
import com.anubhav.app.data.model.AktivBillNumber
import com.anubhav.app.data.model.AktivBookingRequest
import com.anubhav.app.data.model.AktivBookingResponse
import com.anubhav.app.data.model.AktivCollectionCentre
import com.anubhav.app.data.model.AktivDoctor
import com.anubhav.app.data.model.AktivReceptionUser
import com.anubhav.app.data.model.AktivTest
import com.anubhav.app.data.remote.AktivApiClient
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AktivRepository(
    private val api: com.anubhav.app.data.remote.AktivApi = AktivApiClient.api,
) {
    suspend fun login(userid: String, password: String): Result<AktivLoginResponse> =
        runCatching {
            api.login(AktivLoginRequest(userid, password))
        }

    suspend fun listReceptionUsers(): Result<List<AktivReceptionUser>> = runCatching {
        api.listUsers()
    }

    suspend fun searchTests(query: String): Result<List<AktivTest>> = runCatching {
        api.searchTests(query = query, limit = 50)
    }

    suspend fun searchDoctors(query: String): Result<List<AktivDoctor>> = runCatching {
        api.searchDoctors(query = query, limit = 50)
    }

    suspend fun listCollectionCentres(query: String = ""): Result<List<AktivCollectionCentre>> =
        runCatching {
            api.listCollectionCentres(query)
        }

    suspend fun getNextBillNumber(
        billDate: LocalDate? = null,
        testMode: Boolean? = null,
    ): Result<AktivBillNumber> = runCatching {
        val dateStr = billDate?.format(DateTimeFormatter.ISO_LOCAL_DATE)
        api.nextBillNumber(billDate = dateStr, testMode = testMode)
    }

    suspend fun pushBookingToAktiv(request: AktivBookingRequest): Result<AktivBookingResponse> =
        runCatching {
            api.pushBooking(request)
        }

    suspend fun pushBookingToAktiv(
        patientName: String,
        phone: String,
        sex: String,
        ageYear: Int?,
        ageMonth: Int? = null,
        ageDay: Int? = null,
        refrdoctorKey: Int?,
        collcentreKey: Int = 1,
        testKeys: List<Int>,
        billDate: LocalDate? = null,
        billNumber: String? = null,
        amountPaid: Double? = null,
        receiptMode: String = "CASH",
        chequeNo: String? = null,
        remarks: String? = null,
        testMode: Boolean? = null,
        sysUserKey: Int? = null,
    ): Result<AktivBookingResponse> = pushBookingToAktiv(
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

    /** Void receipt only — never deletes the AKTIV bill row. */
    suspend fun cancelBooking(billKey: Int, sysUserKey: Int? = null): Result<Boolean> =
        runCatching {
            val body = if (sysUserKey != null) mapOf("sys_user_key" to sysUserKey) else emptyMap()
            api.cancelBooking(billKey, body)
            true
        }
}
