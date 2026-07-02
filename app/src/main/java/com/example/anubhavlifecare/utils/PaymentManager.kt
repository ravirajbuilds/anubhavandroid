package com.example.anubhavlifecare.utils

import android.app.Activity
import com.example.anubhavlifecare.BuildConfig
import com.razorpay.Checkout
import com.razorpay.PaymentResultListener
import org.json.JSONObject

/**
 * Razorpay checkout for 50% prebook advance and pending bill payments.
 */
class PaymentManager(
    private val activity: Activity,
    private val listener: PaymentResultListener,
) {
  fun startPayment(
        amount: Double,
        name: String,
        email: String,
        phone: String,
        description: String,
        orderNote: String = "",
    ) {
        val checkout = Checkout()
        checkout.setKeyID(BuildConfig.RAZORPAY_KEY_ID)

        val options = JSONObject()
        options.put("name", "Anubhav Life Care")
        options.put("description", description)
        options.put("currency", "INR")
        options.put("amount", (amount * 100).toInt())
        options.put("theme.color", "#1A3A6B")

        val prefill = JSONObject()
        prefill.put("name", name)
        if (email.isNotBlank()) prefill.put("email", email)
        if (phone.isNotBlank()) prefill.put("contact", phone)
        options.put("prefill", prefill)

        if (orderNote.isNotBlank()) {
            val notes = JSONObject()
            notes.put("note", orderNote)
            options.put("notes", notes)
        }

        checkout.open(activity, options)
    }
}
