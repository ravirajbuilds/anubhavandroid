package com.example.anubhavlifecare.utils

import android.util.Log
import com.example.anubhavlifecare.data.model.Booking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EmailManager {

    companion object {
        private const val TAG = "EmailManager"
        private const val EMAIL_USER = "contact.anubhavlife@gmail.com"
        private const val EMAIL_PASS = "bzzvtfogvirfujoc"
        private const val CLINIC_EMAIL = "contact.anubhavlife@gmail.com"
    }

    suspend fun sendBookingConfirmation(booking: Booking): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                // This is a placeholder implementation
                // Will be replaced with actual email sending once dependencies are loaded

                val emailSubject = "Booking Confirmation - AKTIV Admin"
                val emailBody = createBookingConfirmationEmail(booking)

                Log.d(TAG, "Sending booking confirmation email")
                Log.d(TAG, "To: ${booking.userEmail}, $CLINIC_EMAIL")
                Log.d(TAG, "Subject: $emailSubject")
                Log.d(TAG, "Body: $emailBody")

                // Simulate email sending success
                Result.success(true)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to send booking confirmation email", e)
                Result.failure(e)
            }
        }
    }

    suspend fun sendBookingUpdate(booking: Booking): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val emailSubject = "Booking Update - AKTIV Admin"
                val emailBody = createBookingUpdateEmail(booking)

                Log.d(TAG, "Sending booking update email")
                Log.d(TAG, "To: ${booking.userEmail}, $CLINIC_EMAIL")
                Log.d(TAG, "Subject: $emailSubject")
                Log.d(TAG, "Body: $emailBody")

                // Simulate email sending success
                Result.success(true)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to send booking update email", e)
                Result.failure(e)
            }
        }
    }

    private fun createBookingConfirmationEmail(booking: Booking): String {
        return """
            Dear ${booking.userName},
            
            Thank you for booking with AKTIV Admin!
            
            BOOKING DETAILS:
            ================
            Booking ID: ${booking.id}
            Patient Name: ${booking.userName}
            Phone: ${booking.userPhone}
            Email: ${booking.userEmail}
            
            SELECTED TESTS:
            ${booking.selectedTests.joinToString("\n") { "• ${it.name} - ₹${it.price}" }}
            
            APPOINTMENT DETAILS:
            Preferred Date: ${booking.preferredDate}
            Preferred Time: ${booking.preferredTimeSlot}
            Booking Type: ${booking.bookingType}
            
            PAYMENT DETAILS:
            Total Amount: ₹${booking.totalAmount}
            Advance Paid: ₹${booking.advancePaid}
            Payment Status: ${booking.paymentStatus}
            ${if (booking.razorpayPaymentId.isNotEmpty()) "Payment ID: ${booking.razorpayPaymentId}" else ""}
            
            IMPORTANT NOTES:
            ${if (booking.bookingType.name == "REGULAR") "• This booking does not guarantee your preferred time slot\n• Waiting time can be 1-3 hours\n• Bill amount is due before report delivery" else "• Your time slot has been pre-booked\n• Please arrive 15 minutes before your appointment"}
            
            PREPARATION INSTRUCTIONS:
            ${
            booking.selectedTests.joinToString("\n") { test ->
                if (test.preparationInstructions.isNotEmpty()) "• ${test.name}: ${test.preparationInstructions}"
                else "• ${test.name}: No special preparation needed"
            }
        }
            
            For any queries, please contact us:
            📞 +91-9230755875 | +91-9230755870
            📱 WhatsApp: +91-9230755876
            📧 contact.anubhavlife@gmail.com
            🌐 www.anubhavlifecare.in
            
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
                "CONFIRMED" -> "Your booking has been confirmed. Please arrive on time."
                "IN_PROGRESS" -> "Sample collection is in progress."
                "COMPLETED" -> "Your tests are completed. Reports will be available soon."
                "CANCELLED" -> "Your booking has been cancelled. Please contact us for more details."
                else -> "Booking status updated to ${booking.status}"
            }
        }
            
            For any queries, please contact us:
            📞 +91-9230755875 | +91-9230755870
            📱 WhatsApp: +91-9230755876
            
            Best regards,
            AKTIV Admin Team
        """.trimIndent()
    }
}