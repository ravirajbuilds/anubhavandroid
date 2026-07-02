package com.example.anubhavlifecare.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.anubhavlifecare.MainActivity
import com.example.anubhavlifecare.R
import com.example.anubhavlifecare.data.repository.CustomerRepository
import com.example.anubhavlifecare.utils.CustomerSessionManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Customer login via Firebase Phone OTP or Email.
 * Phone OTP is verified client-side by Firebase SDK (Play Integrity / SHA hash in Firebase console).
 */
class LoginActivity : AppCompatActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val customerRepo = CustomerRepository()
    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var loginMode = MODE_PHONE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (CustomerSessionManager.isLoggedIn(this)) {
            openMain()
            return
        }

        setContentView(R.layout.activity_login)

        val radioLoginMode = findViewById<RadioGroup>(R.id.radioLoginMode)
        val layoutPhone = findViewById<TextInputLayout>(R.id.layoutPhone)
        val layoutEmail = findViewById<TextInputLayout>(R.id.layoutEmail)
        val layoutOtp = findViewById<TextInputLayout>(R.id.layoutOtp)
        val etPhone = findViewById<TextInputEditText>(R.id.etPhone)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etOtp = findViewById<TextInputEditText>(R.id.etOtp)
        val btnAction = findViewById<MaterialButton>(R.id.btnLogin)
        val progress = findViewById<ProgressBar>(R.id.progressLogin)
        val tvHint = findViewById<TextView>(R.id.tvLoginHint)

        fun updateMode() {
            val isPhone = loginMode == MODE_PHONE
            layoutPhone.visibility = if (isPhone) View.VISIBLE else View.GONE
            layoutEmail.visibility = if (isPhone) View.GONE else View.VISIBLE
            layoutOtp.visibility = if (isPhone && verificationId != null) View.VISIBLE else View.GONE
            btnAction.text = when {
                isPhone && verificationId == null -> getString(R.string.send_otp)
                isPhone -> getString(R.string.verify_otp)
                else -> getString(R.string.login_with_email)
            }
            tvHint.text = if (isPhone) {
                getString(R.string.login_phone_hint)
            } else {
                getString(R.string.login_email_hint)
            }
        }

        radioLoginMode.setOnCheckedChangeListener { _, checkedId ->
            loginMode = if (checkedId == R.id.radioPhone) MODE_PHONE else MODE_EMAIL
            verificationId = null
            layoutOtp.visibility = View.GONE
            updateMode()
        }

        btnAction.setOnClickListener {
            progress.visibility = View.VISIBLE
            btnAction.isEnabled = false

            if (loginMode == MODE_PHONE) {
                if (verificationId == null) {
                    val phone = etPhone.text?.toString()?.trim().orEmpty()
                    if (phone.length < 10) {
                        showError(getString(R.string.invalid_phone))
                        progress.visibility = View.GONE
                        btnAction.isEnabled = true
                        return@setOnClickListener
                    }
                    sendPhoneOtp("+91$phone", progress, btnAction) { updateMode() }
                } else {
                    val otp = etOtp.text?.toString()?.trim().orEmpty()
                    if (otp.length < 6) {
                        showError(getString(R.string.invalid_otp))
                        progress.visibility = View.GONE
                        btnAction.isEnabled = true
                        return@setOnClickListener
                    }
                    verifyPhoneOtp(otp, etPhone.text?.toString()?.trim().orEmpty(), progress, btnAction)
                }
            } else {
                val email = etEmail.text?.toString()?.trim().orEmpty()
                if (!email.contains("@")) {
                    showError(getString(R.string.invalid_email))
                    progress.visibility = View.GONE
                    btnAction.isEnabled = true
                    return@setOnClickListener
                }
                loginWithEmail(email, progress, btnAction)
            }
        }

        updateMode()
        handleEmailLink(intent)
    }

    private fun sendPhoneOtp(
        phoneE164: String,
        progress: ProgressBar,
        btn: MaterialButton,
        onSent: () -> Unit,
    ) {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                progress.visibility = View.GONE
                btn.isEnabled = true
                signInWithPhoneCredential(credential, phoneE164.removePrefix("+91"))
            }

            override fun onVerificationFailed(e: FirebaseException) {
                progress.visibility = View.GONE
                btn.isEnabled = true
                showError(e.message ?: getString(R.string.login_failed))
            }

            override fun onCodeSent(
                id: String,
                token: PhoneAuthProvider.ForceResendingToken,
            ) {
                verificationId = id
                resendToken = token
                progress.visibility = View.GONE
                btn.isEnabled = true
                Toast.makeText(this@LoginActivity, R.string.otp_sent, Toast.LENGTH_SHORT).show()
                onSent()
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneE164)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun verifyPhoneOtp(
        otp: String,
        phone: String,
        progress: ProgressBar,
        btn: MaterialButton,
    ) {
        val id = verificationId
        if (id == null) {
            progress.visibility = View.GONE
            btn.isEnabled = true
            return
        }
        val credential = PhoneAuthProvider.getCredential(id, otp)
        signInWithPhoneCredential(credential, phone)
        progress.visibility = View.GONE
        btn.isEnabled = true
    }

    private fun signInWithPhoneCredential(credential: PhoneAuthCredential, phone: String) {
        lifecycleScope.launch {
            try {
                val result = auth.signInWithCredential(credential).await()
                val user = result.user ?: throw IllegalStateException("No user")
                val profile = customerRepo.getProfile(phone = phone, email = user.email).getOrNull()
                CustomerSessionManager.save(
                    this@LoginActivity,
                    phone = phone,
                    email = user.email,
                    name = profile?.patientName,
                    firebaseUid = user.uid,
                )
                openMain()
            } catch (e: Exception) {
                showError(e.message ?: getString(R.string.login_failed))
            }
        }
    }

    private fun loginWithEmail(
        email: String,
        progress: ProgressBar,
        btn: MaterialButton,
    ) {
        lifecycleScope.launch {
            try {
                // Passwordless email: send 6-digit style via email link; for app UX use email+temp password flow
                // Using Firebase email link — user gets email, returns via same device browser session
                val actionCodeSettings = com.google.firebase.auth.ActionCodeSettings.newBuilder()
                    .setUrl("https://anubhavlifecare.in/finishSignIn?email=$email")
                    .setHandleCodeInApp(true)
                    .setAndroidPackageName(packageName, true, null)
                    .build()

                auth.sendSignInLinkToEmail(email, actionCodeSettings).await()
                getSharedPreferences("email_login", MODE_PRIVATE)
                    .edit()
                    .putString("pending_email", email)
                    .apply()

                progress.visibility = View.GONE
                btn.isEnabled = true
                Toast.makeText(
                    this@LoginActivity,
                    R.string.email_link_sent,
                    Toast.LENGTH_LONG,
                ).show()
            } catch (e: Exception) {
                progress.visibility = View.GONE
                btn.isEnabled = true
                showError(e.message ?: getString(R.string.login_failed))
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleEmailLink(intent)
    }

    private fun handleEmailLink(intent: Intent?) {
        val link = intent?.data?.toString() ?: return
        if (!auth.isSignInWithEmailLink(link)) return

        val email = getSharedPreferences("email_login", MODE_PRIVATE)
            .getString("pending_email", null) ?: return

        lifecycleScope.launch {
            try {
                val result = auth.signInWithEmailLink(email, link).await()
                val user = result.user ?: return@launch
                val profile = customerRepo.getProfile(phone = null, email = email).getOrNull()
                CustomerSessionManager.save(
                    this@LoginActivity,
                    phone = profile?.phone,
                    email = email,
                    name = profile?.patientName,
                    firebaseUid = user.uid,
                )
                openMain()
            } catch (e: Exception) {
                showError(e.message ?: getString(R.string.login_failed))
            }
        }
    }

    private fun showError(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        private const val MODE_PHONE = 0
        private const val MODE_EMAIL = 1
    }
}
