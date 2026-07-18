package com.anubhav.app.utils

import android.content.Context
import com.anubhav.app.data.model.CollectorPatient
import com.anubhav.app.data.model.CollectorPatientRequest
import com.anubhav.app.data.model.CustomerBill
import com.anubhav.app.data.model.CustomerReport
import com.anubhav.app.data.model.CustomerVisit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.lang.reflect.Type

object ReportCache {
    private const val DIR = "report_cache"
    private val gson = Gson()
    private val billListType = object : TypeToken<List<CustomerBill>>() {}.type
    private val collectorPatientListType = object : TypeToken<List<CollectorPatient>>() {}.type
    private val collectorPatientRequestListType = object : TypeToken<List<CollectorPatientRequest>>() {}.type
    private val reportListType = object : TypeToken<List<CustomerReport>>() {}.type
    private val visitListType = object : TypeToken<List<CustomerVisit>>() {}.type

    fun read(context: Context, key: String): List<CustomerReport>? =
        readJson(context, key, reportListType)

    /** The patient's retrieved visit/report history, kept on-device (never our cloud). */
    fun readVisits(context: Context, key: String): List<CustomerVisit>? =
        readJson(context, key, visitListType)

    fun writeVisits(context: Context, key: String, visits: List<CustomerVisit>) {
        writeJson(context, key, visits)
    }

    fun write(context: Context, key: String, reports: List<CustomerReport>) {
        writeJson(context, key, reports)
    }

    fun readBills(context: Context, key: String): List<CustomerBill>? =
        readJson(context, key, billListType)

    fun writeBills(context: Context, key: String, bills: List<CustomerBill>) {
        writeJson(context, key, bills)
    }

    fun readCollectorPatients(context: Context, key: String): List<CollectorPatient>? =
        readJson(context, key, collectorPatientListType)

    fun writeCollectorPatients(context: Context, key: String, patients: List<CollectorPatient>) {
        writeJson(context, key, patients)
    }

    fun readQueuedCollectorPatients(context: Context, key: String): List<CollectorPatientRequest> =
        readJson(context, key, collectorPatientRequestListType) ?: emptyList()

    fun writeQueuedCollectorPatients(
        context: Context,
        key: String,
        patients: List<CollectorPatientRequest>,
    ) {
        writeJson(context, key, patients)
    }

fun removePendingBill(context: Context, phone: String, billKey: Int) {
val pendingKey = "pending_$phone"
readBills(context, pendingKey)
?.filterNot { it.billKey == billKey }
?.let { writeBills(context, pendingKey, it) }

val billsKey = "bills_$phone"
readBills(context, billsKey)
?.map { bill ->
if (bill.billKey == billKey) {
bill.copy(receivedAmount = bill.netAmount, pendingAmount = 0.0)
} else {
bill
}
}
?.let { writeBills(context, billsKey, it) }
}

    private fun <T> readJson(context: Context, key: String, type: Type): T? {
        val file = cacheFile(context, key)
        if (!file.exists()) return null
return runCatching {
@Suppress("UNCHECKED_CAST")
gson.fromJson<T>(file.readText(), type)
}.onFailure {
file.delete()
}.getOrNull()
}

    private fun writeJson(context: Context, key: String, value: Any) {
        val file = cacheFile(context, key)
        file.parentFile?.mkdirs()
        file.writeText(gson.toJson(value))
    }

    private fun cacheFile(context: Context, key: String): File {
        val safeKey = key.lowercase().replace(Regex("[^a-z0-9_-]"), "_")
        return File(File(context.filesDir, DIR), "$safeKey.json")
    }
}
