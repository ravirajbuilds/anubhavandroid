package com.example.anubhavlifecare.ui.customer

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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.anubhavlifecare.MainActivity
import com.example.anubhavlifecare.R
import com.example.anubhavlifecare.data.model.AktivTest
import com.example.anubhavlifecare.data.model.CustomerPrebookRequest
import com.example.anubhavlifecare.data.repository.AktivRepository
import com.example.anubhavlifecare.data.repository.CustomerRepository
import com.example.anubhavlifecare.ui.booking.AktivTestAdapter
import com.example.anubhavlifecare.utils.CustomerSessionManager
import com.example.anubhavlifecare.utils.PaymentManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.razorpay.PaymentResultListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CustomerPrebookFragment : Fragment(), PaymentResultListener {
    private val aktivRepo = AktivRepository()
    private val customerRepo = CustomerRepository()
    private val selectedTests = linkedMapOf<Int, AktivTest>()
    private var searchJob: Job? = null
    private var pendingAdvance = 0.0
    private var selectedDate: String? = null
    private var selectedSlot: String? = null
    private lateinit var testAdapter: AktivTestAdapter

    private var testAdapterItems: List<AktivTest> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_customer_prebook, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? MainActivity)?.setPaymentListener(this)

        val etName = view.findViewById<TextInputEditText>(R.id.etPatientName)
        val etAge = view.findViewById<TextInputEditText>(R.id.etAgeYear)
        val spinnerSex = view.findViewById<AutoCompleteTextView>(R.id.spinnerSex)
        val spinnerDate = view.findViewById<AutoCompleteTextView>(R.id.spinnerDate)
        val spinnerSlot = view.findViewById<AutoCompleteTextView>(R.id.spinnerSlot)
        val etTestSearch = view.findViewById<TextInputEditText>(R.id.etTestSearch)
        val rvTests = view.findViewById<RecyclerView>(R.id.rvTests)
        val tvSelected = view.findViewById<TextView>(R.id.tvSelectedTests)
        val tvTotal = view.findViewById<TextView>(R.id.tvTotalAmount)
        val tvAdvance = view.findViewById<TextView>(R.id.tvAdvanceAmount)
        val tvPolicy = view.findViewById<TextView>(R.id.tvPrebookPolicy)
        val btnPay = view.findViewById<MaterialButton>(R.id.btnPayAdvance)
        val progress = view.findViewById<ProgressBar>(R.id.progressBar)

        CustomerSessionManager.getName(requireContext())?.let { etName.setText(it) }

        spinnerSex.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, listOf("MALE", "FEMALE")),
        )
        spinnerSex.setText("MALE", false)

        tvPolicy.text = getString(
            R.string.prebook_policy,
            getString(R.string.reschedule_phone),
        )

        testAdapter = AktivTestAdapter { test ->
            if (selectedTests.containsKey(test.testKey)) {
                selectedTests.remove(test.testKey)
            } else {
                selectedTests[test.testKey] = test
            }
            testAdapter.submit(testAdapterItems, selectedTests.values.toList())
            updateTotals(tvSelected, tvTotal, tvAdvance)
        }
        rvTests.layoutManager = LinearLayoutManager(requireContext())
        rvTests.adapter = testAdapter

        etTestSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchJob?.cancel()
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(300)
                    aktivRepo.searchTests(s?.toString().orEmpty()).onSuccess {
                        testAdapterItems = it
                        testAdapter.submit(it, selectedTests.values.toList())
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        viewLifecycleOwner.lifecycleScope.launch {
            progress.visibility = View.VISIBLE
            customerRepo.getPrebookCalendar().onSuccess { cal ->
                val dateLabels = cal.dates.map { it.date }
                spinnerDate.setAdapter(
                    ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, dateLabels),
                )
                spinnerDate.setOnItemClickListener { _, _, pos, _ ->
                    selectedDate = cal.dates[pos].date
                    val slotLabels = cal.dates[pos].slots
                        .filter { it.available }
                        .map { "${it.label} (${it.remaining} left)" }
                    spinnerSlot.setAdapter(
                        ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, slotLabels),
                    )
                    spinnerSlot.setOnItemClickListener { _, _, slotPos, _ ->
                        val available = cal.dates[pos].slots.filter { it.available }
                        selectedSlot = available[slotPos].timeSlot
                    }
                }
            }
            aktivRepo.searchTests("").onSuccess {
                testAdapterItems = it
                testAdapter.submit(it, selectedTests.values.toList())
            }
            progress.visibility = View.GONE
        }

        btnPay.setOnClickListener {
            val phone = CustomerSessionManager.getPhone(requireContext())
            if (phone.isNullOrBlank()) {
                Toast.makeText(requireContext(), R.string.phone_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val name = etName.text?.toString()?.trim().orEmpty()
            if (name.isBlank() || selectedTests.isEmpty() || selectedDate == null || selectedSlot == null) {
                Toast.makeText(requireContext(), R.string.fill_all_fields, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val total = selectedTests.values.sumOf { it.rate }
            pendingAdvance = total * 0.5
            PaymentManager(requireActivity(), this).startPayment(
                amount = pendingAdvance,
                name = name,
                email = CustomerSessionManager.getEmail(requireContext()).orEmpty(),
                phone = phone,
                description = "50% prebook advance (non-refundable)",
                orderNote = "PREBOOK $selectedDate $selectedSlot",
            )
        }
    }

    private fun updateTotals(tvSelected: TextView, tvTotal: TextView, tvAdvance: TextView) {
        val names = selectedTests.values.joinToString(", ") { it.testName }
        val total = selectedTests.values.sumOf { it.rate }
        tvSelected.text = names.ifBlank { getString(R.string.no_tests_selected) }
        tvTotal.text = getString(R.string.total_amount_value, total)
        tvAdvance.text = getString(R.string.advance_amount_value, total * 0.5)
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?) {
        val phone = CustomerSessionManager.getPhone(requireContext()) ?: return
        val view = requireView()
        val etName = view.findViewById<TextInputEditText>(R.id.etPatientName)
        val etAge = view.findViewById<TextInputEditText>(R.id.etAgeYear)
        val spinnerSex = view.findViewById<AutoCompleteTextView>(R.id.spinnerSex)
        val progress = view.findViewById<ProgressBar>(R.id.progressBar)

        viewLifecycleOwner.lifecycleScope.launch {
            progress.visibility = View.VISIBLE
            val request = CustomerPrebookRequest(
                patientName = etName.text?.toString()?.trim().orEmpty(),
                phone = phone,
                sex = spinnerSex.text?.toString() ?: "MALE",
                ageYear = etAge.text?.toString()?.toIntOrNull(),
                testKeys = selectedTests.keys.toList(),
                slotDate = selectedDate!!,
                timeSlot = selectedSlot!!,
                paymentId = razorpayPaymentId ?: "unknown",
                amountPaid = pendingAdvance,
                email = CustomerSessionManager.getEmail(requireContext()),
            )
            customerRepo.createPrebook(request).fold(
                onSuccess = { resp ->
                    progress.visibility = View.GONE
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.prebook_success, resp.alcCode, resp.reschedulePhone),
                        Toast.LENGTH_LONG,
                    ).show()
                    selectedTests.clear()
                },
                onFailure = { e ->
                    progress.visibility = View.GONE
                    Toast.makeText(requireContext(), e.message, Toast.LENGTH_LONG).show()
                },
            )
        }
    }

    override fun onDestroyView() {
        (activity as? MainActivity)?.setPaymentListener(null)
        super.onDestroyView()
    }

    override fun onPaymentError(code: Int, description: String?) {
        Toast.makeText(requireContext(), description ?: getString(R.string.payment_failed), Toast.LENGTH_LONG).show()
    }
}
