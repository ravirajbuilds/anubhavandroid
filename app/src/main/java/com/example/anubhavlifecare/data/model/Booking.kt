package com.example.anubhavlifecare.data.model

data class Booking(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhone: String = "",
    val userEmail: String = "",
    val selectedTests: List<Test> = emptyList(),
    val preferredDate: String = "",
    val preferredTimeSlot: String = "",
    val bookingType: BookingType = BookingType.REGULAR,
    val totalAmount: Double = 0.0,
    val advancePaid: Double = 0.0,
    val paymentStatus: PaymentStatus = PaymentStatus.PENDING,
    val razorpayPaymentId: String = "",
    val razorpayOrderId: String = "",
    val status: BookingStatus = BookingStatus.PENDING,
    val createdAt: String = "",
    val updatedAt: String = ""
)

enum class BookingType {
    REGULAR,
    TIME_SLOT_PREBOOK,
    DOCTOR_PREBOOK
}

enum class PaymentStatus {
    PENDING,
    PAID,
    FAILED,
    REFUNDED
}

enum class BookingStatus {
    PENDING,
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}