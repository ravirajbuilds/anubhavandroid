package com.example.anubhavlifecare.ui.booking

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.anubhavlifecare.BuildConfig
import com.example.anubhavlifecare.data.model.AktivBillNumber
import com.example.anubhavlifecare.data.model.AktivBookingResponse
import com.example.anubhavlifecare.data.model.AktivCollectionCentre
import com.example.anubhavlifecare.data.model.AktivDoctor
import com.example.anubhavlifecare.data.model.AktivTest
import com.example.anubhavlifecare.data.repository.AktivRepository
import com.example.anubhavlifecare.utils.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

data class BookTestUiState(
    val billNumber: AktivBillNumber? = null,
    val billDate: LocalDate = TEST_BILL_DATE,
    val testMode: Boolean = BuildConfig.DEBUG,
    val loggedInUser: String = "",
    val tests: List<AktivTest> = emptyList(),
    val doctors: List<AktivDoctor> = emptyList(),
    val collectionCentres: List<AktivCollectionCentre> = emptyList(),
    val selectedTests: List<AktivTest> = emptyList(),
    val selectedDoctor: AktivDoctor? = null,
    val selectedCentre: AktivCollectionCentre? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: AktivBookingResponse? = null,
) {
    companion object {
        val TEST_BILL_DATE: LocalDate = LocalDate.of(2025, 7, 2)
    }
}

class BookTestViewModel(
    application: Application,
    private val aktivRepository: AktivRepository = AktivRepository(),
) : AndroidViewModel(application) {
    private val _state = MutableLiveData(BookTestUiState())
    val state: LiveData<BookTestUiState> = _state

    private var doctorSearchJob: Job? = null
    private var testSearchJob: Job? = null

    init {
        loadMasters()
    }

    private fun sysUserKey(): Int? = SessionManager.getUserKey(getApplication())

    fun loadMasters() {
        viewModelScope.launch {
            val current = _state.value ?: BookTestUiState()
            _state.value = current.copy(isLoading = true, error = null)

            val testMode = current.testMode
            val billDate = if (testMode) BookTestUiState.TEST_BILL_DATE else current.billDate
            val bill = aktivRepository.getNextBillNumber(billDate, testMode).getOrNull()
            val centres = aktivRepository.listCollectionCentres().getOrElse { emptyList() }
            val defaultCentre = centres.firstOrNull { it.collcentreName == "ANUBHAV LIFE CARE" }
                ?: centres.firstOrNull()
            val loggedIn = SessionManager.getUsername(getApplication())
                ?: SessionManager.getUserid(getApplication()).orEmpty()

            _state.value = current.copy(
                isLoading = false,
                billDate = billDate,
                billNumber = bill,
                collectionCentres = centres,
                selectedCentre = defaultCentre,
                loggedInUser = loggedIn,
            )
        }
    }

    fun searchTests(query: String) {
        testSearchJob?.cancel()
        testSearchJob = viewModelScope.launch {
            delay(300)
            val tests = aktivRepository.searchTests(query).getOrElse { emptyList() }
            _state.value = _state.value?.copy(tests = tests)
        }
    }

    fun searchDoctors(query: String) {
        doctorSearchJob?.cancel()
        doctorSearchJob = viewModelScope.launch {
            delay(300)
            val doctors = aktivRepository.searchDoctors(query).getOrElse { emptyList() }
            _state.value = _state.value?.copy(doctors = doctors)
        }
    }

    fun selectDoctor(doctor: AktivDoctor?) {
        _state.value = _state.value?.copy(selectedDoctor = doctor)
    }

    fun selectCentre(centre: AktivCollectionCentre?) {
        _state.value = _state.value?.copy(selectedCentre = centre)
    }

    fun toggleTest(test: AktivTest) {
        val current = _state.value?.selectedTests.orEmpty()
        val updated = if (current.any { it.testKey == test.testKey }) {
            current.filterNot { it.testKey == test.testKey }
        } else {
            current + test
        }
        _state.value = _state.value?.copy(selectedTests = updated)
    }

    fun submitBooking(
        patientName: String,
        phone: String,
        sex: String,
        ageYear: Int?,
        ageMonth: Int?,
        ageDay: Int?,
        amountPaid: Double?,
        receiptMode: String,
        chequeNo: String?,
        remarks: String?,
    ) {
        val current = _state.value ?: return
        val userKey = sysUserKey()
        if (userKey == null) {
            _state.value = current.copy(error = "Not logged in")
            return
        }
        if (patientName.isBlank() || phone.isBlank()) {
            _state.value = current.copy(error = "Name and phone are required")
            return
        }
        if (current.selectedTests.isEmpty()) {
            _state.value = current.copy(error = "Select at least one test")
            return
        }

        val isTest = current.testMode
        val upiCheque = if (receiptMode.equals("UPI", ignoreCase = true)) chequeNo else null
        val paid = if (isTest) 0.0 else (amountPaid ?: current.selectedTests.sumOf { it.rate })

        viewModelScope.launch {
            _state.value = current.copy(isLoading = true, error = null, success = null)
            val result = aktivRepository.pushBookingToAktiv(
                patientName = patientName.trim(),
                phone = phone.trim(),
                sex = sex,
                ageYear = ageYear,
                ageMonth = ageMonth,
                ageDay = ageDay,
                refrdoctorKey = current.selectedDoctor?.refrdoctorKey,
                collcentreKey = current.selectedCentre?.collcentreKey ?: 1,
                testKeys = current.selectedTests.map { it.testKey },
                billDate = current.billDate,
                billNumber = current.billNumber?.billNumber,
                amountPaid = paid,
                receiptMode = receiptMode,
                chequeNo = upiCheque,
                remarks = remarks,
                testMode = isTest,
                sysUserKey = userKey,
            )
            result.fold(
                onSuccess = { response ->
                    _state.value = _state.value?.copy(isLoading = false, success = response)
                    loadMasters()
                },
                onFailure = { err ->
                    _state.value = _state.value?.copy(
                        isLoading = false,
                        error = err.message ?: "Booking failed",
                    )
                },
            )
        }
    }

    fun clearMessages() {
        _state.value = _state.value?.copy(error = null, success = null)
    }
}
