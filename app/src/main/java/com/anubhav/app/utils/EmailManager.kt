package com.anubhav.app.utils

import com.anubhav.app.data.model.Booking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EmailManager {

    companion object {
        private const val CLINIC_EMAIL = "contact.anubhavlife@gmail.com"
    }

    suspend fun sendBookingConfirmation(booking: Booking): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                createBookingConfirmationEmail(booking)
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun sendBookingUpdate(booking: Booking): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                createBookingUpdateEmail(booking)
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun createBookingConfirmationEmail(booking: Booking): String {
        return """
            Dear ${booking.userName},

            Thank you for booking with AKTIV Admin.

            BOOKING DETAILS:
            ================
            Booking ID: ${booking.id}
            Booking Type: ${booking.bookingType}
            Status: ${booking.status}
            Date: ${booking.preferredDate}
            Time: ${booking.preferredTimeSlot}

            TESTS BOOKED:
            ${booking.selectedTests.joinToString("\n") { test -> "- ${test.name}: ₹${test.price}" }}

            PAYMENT DETAILS:
            Total Amount: ₹${booking.totalAmount}
            Advance Paid: ₹${booking.advancePaid}
            Payment Status: ${booking.paymentStatus}
            ${if (booking.razorpayPaymentId.isNotEmpty()) "Payment ID: ${booking.razorpayPaymentId}" else ""}

            IMPORTANT NOTES:
            ${
                if (booking.bookingType.name == "REGULAR") {
                    "- This booking does not guarantee preferred time slot\n- Waiting time can be 1-3 hours\n- Bill amount due before report delivery"
                } else {
                    "- Your time slot has been pre-booked\n- Please arrive 15 minutes before your appointment"
                }
            }

            PREPARATION INSTRUCTIONS:
            ${
                booking.selectedTests.joinToString("\n") { test ->
                    if (test.preparationInstructions.isNotEmpty()) {
                        "- ${test.name}: ${test.preparationInstructions}"
                    } else {
                        "- ${test.name}: No special preparation needed"
                    }
                }
            }

            For any queries, please contact us:
            Phone: +91-9230755875 | +91-9230755870
            WhatsApp: +91-9230755876
            Email: $CLINIC_EMAIL
            Web: www.anubhavlifecare.in

            Best regards,
            AKTIV Admin Team
        """.trimIndent()
    }

    private fun createBookingUpdateEmail(booking: Booking): String {
        return """
            Dear ${booking.userName},

            Your booking status has been updated.

            BOOKING DETAILS:
            ================
            Booking ID: ${booking.id}
            Current Status: ${booking.status}

            ${
                when (booking.status.name) {
                    "CONFIRMED" -> "Your booking is confirmed. Please arrive on time."
                    "IN_PROGRESS" -> "Sample collection is in progress."
                    "COMPLETED" -> "Your tests are completed. Reports will be available soon."
                    "CANCELLED" -> "Your booking has been cancelled. Please contact us for more details."
                    else -> "Booking status updated: ${booking.status}"
                }
            }

            For any queries, please contact us:
            Phone: +91-9230755875 | +91-9230755870
            WhatsApp: +91-9230755876
            Email: $CLINIC_EMAIL

            Best regards,
            AKTIV Admin Team
        """.trimIndent()
    }
}
