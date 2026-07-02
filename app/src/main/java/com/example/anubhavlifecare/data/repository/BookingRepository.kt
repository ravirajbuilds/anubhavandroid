package com.example.anubhavlifecare.data.repository

import com.example.anubhavlifecare.data.model.AktivBookingRequest
import com.example.anubhavlifecare.data.model.Booking
import com.example.anubhavlifecare.data.model.BookingStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class BookingRepository(
    private val aktivRepository: AktivRepository = AktivRepository(),
) {
    suspend fun createBooking(booking: Booking): Result<Booking> = runCatching {
        val testKeys = booking.selectedTests.mapNotNull { it.id.toIntOrNull() }
        require(testKeys.isNotEmpty()) { "No valid AKTIV tests selected" }

        val response = aktivRepository.pushBookingToAktiv(
            AktivBookingRequest(
                patientName = booking.userName,
                phone = booking.userPhone,
                sex = "MALE",
                ageYear = null,
                testKeys = testKeys,
                billDate = booking.preferredDate.ifBlank {
                    LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                },
                amountPaid = booking.totalAmount,
                receiptMode = if (booking.razorpayPaymentId.isNotBlank()) "UPI" else "CASH",
                chequeNo = booking.razorpayPaymentId.ifBlank { null },
            ),
        ).getOrThrow()

        booking.copy(
            id = response.billNo,
            status = BookingStatus.CONFIRMED,
            createdAt = System.currentTimeMillis().toString(),
            updatedAt = System.currentTimeMillis().toString(),
        )
    }

    suspend fun updateBooking(booking: Booking): Result<Booking> = runCatching {
        booking.copy(updatedAt = System.currentTimeMillis().toString())
    }

    suspend fun getUserBookings(userId: String): Result<List<Booking>> =
        Result.success(emptyList())

    suspend fun getBookingById(bookingId: String): Result<Booking?> =
        Result.success(null)

    suspend fun cancelBooking(bookingId: String): Result<Boolean> =
        bookingId.toIntOrNull()?.let { billKey ->
            aktivRepository.cancelBooking(billKey)
        } ?: Result.success(true)
}
