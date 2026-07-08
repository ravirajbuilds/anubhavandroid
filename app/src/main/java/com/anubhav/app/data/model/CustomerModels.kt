package com.anubhav.app.data.model

import com.google.gson.annotations.SerializedName

data class CustomerProfile(
    val found: Boolean,
    @SerializedName("patient_name") val patientName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    @SerializedName("registration_no") val registrationNo: String? = null,
    @SerializedName("regt_key") val regtKey: Int? = null,
)

data class CustomerBill(
    @SerializedName("bill_key") val billKey: Int,
    @SerializedName("bill_no") val billNo: String?,
    @SerializedName("billdate") val billDate: String?,
    @SerializedName("patientname") val patientName: String?,
    @SerializedName("netamount") val netAmount: Double,
    @SerializedName("receivedamount") val receivedAmount: Double,
    @SerializedName("pending_amount") val pendingAmount: Double,
    val remarks: String? = null,
    @SerializedName("apntdate") val apntDate: String? = null,
    @SerializedName("apnt_no") val apntNo: String? = null,
    @SerializedName("reschedule_phone") val reschedulePhone: String? = null,
    @SerializedName("reschedule_note") val rescheduleNote: String? = null,
)

data class CustomerReport(
    @SerializedName("bill_key") val billKey: Int,
    @SerializedName("bill_no") val billNo: String?,
    @SerializedName("billdate") val billDate: String?,
    @SerializedName("testname") val testName: String?,
    @SerializedName("testcode") val testCode: String?,
    @SerializedName("reportingdate") val reportingDate: String?,
    val status: String?,
)

data class PrebookSlotInfo(
    @SerializedName("time_slot") val timeSlot: String,
    val label: String,
    val capacity: Int,
    val booked: Int,
    val remaining: Int,
    val available: Boolean,
)

data class PrebookDateInfo(
    val date: String,
    val slots: List<PrebookSlotInfo>,
)

data class PrebookCalendar(
    @SerializedName("prebook_days") val prebookDays: List<Int>,
    @SerializedName("advance_fraction") val advanceFraction: Double,
    @SerializedName("advance_non_refundable") val advanceNonRefundable: Boolean,
    @SerializedName("reschedule_phone") val reschedulePhone: String,
    val dates: List<PrebookDateInfo>,
)

data class CustomerPrebookRequest(
    @SerializedName("patient_name") val patientName: String,
    val phone: String,
    val sex: String = "MALE",
    @SerializedName("age_year") val ageYear: Int? = null,
    @SerializedName("test_keys") val testKeys: List<Int>,
    @SerializedName("slot_date") val slotDate: String,
    @SerializedName("time_slot") val timeSlot: String,
    @SerializedName("payment_id") val paymentId: String,
    @SerializedName("amount_paid") val amountPaid: Double,
    val email: String? = null,
)

data class CustomerPrebookResponse(
    val success: Boolean,
    @SerializedName("bill_key") val billKey: Int,
    @SerializedName("bill_no") val billNo: String,
    @SerializedName("alc_code") val alcCode: String,
    @SerializedName("slot_date") val slotDate: String,
    @SerializedName("time_slot") val timeSlot: String,
    @SerializedName("time_slot_label") val timeSlotLabel: String,
    @SerializedName("total_amount") val totalAmount: Double,
    @SerializedName("advance_paid") val advancePaid: Double,
    @SerializedName("balance_due") val balanceDue: Double,
    @SerializedName("reschedule_phone") val reschedulePhone: String,
    @SerializedName("reschedule_note") val rescheduleNote: String,
)

data class CustomerPaymentRequest(
    @SerializedName("bill_key") val billKey: Int,
    val phone: String,
    @SerializedName("amount_paid") val amountPaid: Double,
    @SerializedName("payment_id") val paymentId: String,
)

data class CustomerPaymentResponse(
    val success: Boolean,
    @SerializedName("bill_key") val billKey: Int,
    @SerializedName("amount_paid") val amountPaid: Double,
    @SerializedName("payment_id") val paymentId: String,
)
