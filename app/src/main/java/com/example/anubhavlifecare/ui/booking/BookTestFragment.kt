package com.example.anubhavlifecare.ui.booking

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.anubhavlifecare.R
import com.example.anubhavlifecare.data.model.AktivCollectionCentre
import com.example.anubhavlifecare.data.model.AktivDoctor
import com.example.anubhavlifecare.data.model.AktivTest
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.time.format.DateTimeFormatter

class BookTestFragment : Fragment() {
    private lateinit var viewModel: BookTestViewModel
    private lateinit var testAdapter: AktivTestAdapter

    private var doctors: List<AktivDoctor> = emptyList()
    private var centres: List<AktivCollectionCentre> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_book_test, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application),
        )[BookTestViewModel::class.java]

        val tvLoggedInUser = view.findViewById<TextView>(R.id.tvLoggedInUser)
        val tvTestModeBanner = view.findViewById<TextView>(R.id.tvTestModeBanner)
        val etBillNo = view.findViewById<TextInputEditText>(R.id.etBillNo)
        val etBillDate = view.findViewById<TextInputEditText>(R.id.etBillDate)
        val etPatientName = view.findViewById<TextInputEditText>(R.id.etPatientName)
        val etPhone = view.findViewById<TextInputEditText>(R.id.etPhone)
        val spinnerSex = view.findViewById<AutoCompleteTextView>(R.id.spinnerSex)
        val etAgeYear = view.findViewById<TextInputEditText>(R.id.etAgeYear)
        val etAgeMonth = view.findViewById<TextInputEditText>(R.id.etAgeMonth)
        val etAgeDay = view.findViewById<TextInputEditText>(R.id.etAgeDay)
        val spinnerDoctor = view.findViewById<AutoCompleteTextView>(R.id.spinnerDoctor)
        val spinnerCentre = view.findViewById<AutoCompleteTextView>(R.id.spinnerCollectionCentre)
        val etTestSearch = view.findViewById<TextInputEditText>(R.id.etTestSearch)
        val rvTests = view.findViewById<RecyclerView>(R.id.rvTests)
        val tvSelected = view.findViewById<TextView>(R.id.tvSelectedTests)
        val etAmountPaid = view.findViewById<TextInputEditText>(R.id.etAmountPaid)
        val spinnerReceipt = view.findViewById<AutoCompleteTextView>(R.id.spinnerReceiptMode)
        val layoutChequeRef = view.findViewById<TextInputLayout>(R.id.layoutChequeRef)
        val etChequeNo = view.findViewById<TextInputEditText>(R.id.etChequeNo)
        val etRemarks = view.findViewById<TextInputEditText>(R.id.etRemarks)
        val btnSubmit = view.findViewById<MaterialButton>(R.id.btnSubmitBooking)
        val progress = view.findViewById<ProgressBar>(R.id.progressBar)

        spinnerSex.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, listOf("MALE", "FEMALE")),
        )
        spinnerSex.setText("MALE", false)

        spinnerReceipt.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, listOf("CASH", "UPI", "CARD")),
        )
        spinnerReceipt.setText("CASH", false)

        fun updateChequeVisibility(mode: String) {
            val isUpi = mode.equals("UPI", ignoreCase = true)
            layoutChequeRef.visibility = if (isUpi) View.VISIBLE else View.GONE
            if (!isUpi) etChequeNo.text?.clear()
        }
        spinnerReceipt.setOnItemClickListener { _, _, _, _ ->
            updateChequeVisibility(spinnerReceipt.text?.toString().orEmpty())
        }
        updateChequeVisibility("CASH")

        testAdapter = AktivTestAdapter { test -> viewModel.toggleTest(test) }
        rvTests.layoutManager = LinearLayoutManager(requireContext())
        rvTests.adapter = testAdapter

        etTestSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.searchTests(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        spinnerDoctor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.searchDoctors(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        btnSubmit.setOnClickListener {
            viewModel.submitBooking(
                patientName = etPatientName.text?.toString().orEmpty(),
                phone = etPhone.text?.toString().orEmpty(),
                sex = spinnerSex.text?.toString().orEmpty(),
                ageYear = etAgeYear.text?.toString()?.toIntOrNull(),
                ageMonth = etAgeMonth.text?.toString()?.toIntOrNull(),
                ageDay = etAgeDay.text?.toString()?.toIntOrNull(),
                amountPaid = etAmountPaid.text?.toString()?.toDoubleOrNull(),
                receiptMode = spinnerReceipt.text?.toString().orEmpty(),
                chequeNo = etChequeNo.text?.toString(),
                remarks = etRemarks.text?.toString(),
            )
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            progress.visibility = if (state.isLoading) View.VISIBLE else View.GONE
            btnSubmit.isEnabled = !state.isLoading

            tvTestModeBanner.visibility = if (state.testMode) View.VISIBLE else View.GONE
            if (state.loggedInUser.isNotBlank()) {
                tvLoggedInUser.text = getString(R.string.logged_in_as, state.loggedInUser)
                tvLoggedInUser.visibility = View.VISIBLE
            }
            etBillDate.setText(
                state.billDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
            )

            state.billNumber?.let { bill -> etBillNo.setText(bill.billNo) }

            if (state.testMode) {
                etAmountPaid.setText("0")
                etAmountPaid.isEnabled = false
                btnSubmit.text = "Push TEST to AKTIV (₹0)"
            } else {
                etAmountPaid.isEnabled = true
                btnSubmit.text = "Push to AKTIV"
            }

            testAdapter.submit(state.tests, state.selectedTests)
            val total = state.selectedTests.sumOf { it.rate }
            tvSelected.text = if (state.selectedTests.isEmpty()) {
                "Selected: none"
            } else if (state.testMode) {
                "Selected: ${state.selectedTests.size} test(s) — ₹$total (discounted to ₹0)"
            } else {
                "Selected: ${state.selectedTests.size} test(s) — ₹$total"
            }

            if (state.collectionCentres != centres) {
                centres = state.collectionCentres
                val names = centres.map { it.collcentreName }
                spinnerCentre.setAdapter(
                    ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, names),
                )
                state.selectedCentre?.let { spinnerCentre.setText(it.collcentreName, false) }
                spinnerCentre.setOnItemClickListener { _, _, position, _ ->
                    viewModel.selectCentre(centres.getOrNull(position))
                }
            }

            if (state.doctors != doctors) {
                doctors = state.doctors
                val names = doctors.map { it.displayName }
                spinnerDoctor.setAdapter(
                    ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, names),
                )
            }
            spinnerDoctor.setOnItemClickListener { _, _, position, _ ->
                viewModel.selectDoctor(doctors.getOrNull(position))
            }

            state.error?.let { msg ->
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                viewModel.clearMessages()
            }
            state.success?.let { response ->
                val label = if (response.testMode) "TEST booked" else "Booked"
                Toast.makeText(
                    requireContext(),
                    "$label ${response.billNo} (Apnt: ${response.apntDate}, net ₹${response.netAmount})",
                    Toast.LENGTH_LONG,
                ).show()
                viewModel.clearMessages()
            }
        }

        viewModel.searchTests("")
        viewModel.searchDoctors("")
    }
}
