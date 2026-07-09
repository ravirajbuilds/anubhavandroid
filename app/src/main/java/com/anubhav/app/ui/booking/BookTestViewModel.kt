package com.anubhav.app.ui.booking

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.anubhav.app.data.model.AktivBillNumber
import com.anubhav.app.data.model.AktivBookingResponse
import com.anubhav.app.data.model.AktivCollectionCentre
import com.anubhav.app.data.model.AktivDoctor
import com.anubhav.app.data.model.AktivTest
import com.anubhav.app.data.repository.AktivRepository
import com.anubhav.app.utils.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

data class BookTestUiState(
    val billNumber: AktivBillNumber? = null,
    val billDate: LocalDate = LocalDate.now(),
    val testMode: Boolean = false,
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
)

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

    fun loadMasters(
        billDate: LocalDate = _state.value?.billDate ?: LocalDate.now(),
        testMode: Boolean = _state.value?.testMode ?: false,
    ) {
        _state.value = _state.value?.copy(isLoading = true, billDate = billDate, testMode = testMode)
        viewModelScope.launch {
            val user = SessionManager.getUsername(getApplication()).orEmpty()
            val bill = aktivRepository.nextBillNumber(billDate, testMode).getOrNull()
            val tests = aktivRepository.searchTests("").getOrElse { emptyList() }
            val doctors = aktivRepository.searchDoctors("").getOrElse { emptyList() }
            val centres = aktivRepository.listCollectionCentres("").getOrElse { emptyList() }
            _state.value = _state.value?.copy(
                billNumber = bill,
                loggedInUser = user,
                tests = tests,
                doctors = doctors,
                collectionCentres = centres,
                selectedCentre = _state.value?.selectedCentre ?: centres.firstOrNull(),
                isLoading = false,
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

        val billDate = current.billDate
        val billNumber = current.billNumber?.billNumber
        val isTest = current.testMode
        val upiCheque = chequeNo?.takeIf { it.isNotBlank() }
        _state.value = current.copy(isLoading = true, error = null, success = null)
        viewModelScope.launch {
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
                billDate = billDate,
                billNumber = billNumber,
                amountPaid = amountPaid,
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

    private fun sysUserKey(): Int? = SessionManager.getUserKey(getApplication())
}
