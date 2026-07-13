package com.anubhav.app.ui.customer

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
import com.anubhav.app.MainActivity
import com.anubhav.app.R
import com.anubhav.app.data.model.AktivTest
import com.anubhav.app.data.model.CustomerPrebookRequest
import com.anubhav.app.data.model.PrebookCalendar
import com.anubhav.app.data.repository.AktivRepository
import com.anubhav.app.data.repository.CustomerRepository
import com.anubhav.app.ui.booking.AktivTestAdapter
import com.anubhav.app.utils.CustomerSessionManager
import com.anubhav.app.utils.PaymentManager
import com.anubhav.app.utils.localized
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
        val etSearch = view.findViewById<TextInputEditText>(R.id.etTestSearch)
        val rvTests = view.findViewById<RecyclerView>(R.id.rvTests)
        val tvSelected = view.findViewById<TextView>(R.id.tvSelectedTests)
        val tvTotal = view.findViewById<TextView>(R.id.tvTotalAmount)
        val tvAdvance = view.findViewById<TextView>(R.id.tvAdvanceAmount)
        val tvPolicy = view.findViewById<TextView>(R.id.tvPrebookPolicy)
        val btnPay = view.findViewById<MaterialButton>(R.id.btnPayAdvance)
        val progress = view.findViewById<ProgressBar>(R.id.progressBar)

        view.findViewById<TextView>(R.id.tvPrebookTitle).text = localized(R.string.prebook_time_slot)
        tvPolicy.text = localized(R.string.prebook_policy, getString(R.string.reschedule_phone))
        btnPay.text = localized(R.string.pay_advance)
        spinnerSex.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                listOf(localized(R.string.sex_male), localized(R.string.sex_female)),
            ),
        )
        spinnerSex.setText(localized(R.string.sex_male), false)

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
        updateTotals(tvSelected, tvTotal, tvAdvance)
        loadCalendar(progress, spinnerDate, spinnerSlot, tvPolicy)
        searchTests("", progress)

        etSearch.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchJob?.cancel()
                    searchJob = viewLifecycleOwner.lifecycleScope.launch {
                        delay(250)
                        searchTests(s?.toString().orEmpty(), progress)
                    }
                }

                override fun afterTextChanged(s: Editable?) = Unit
            },
        )

        btnPay.setOnClickListener {
            val phone = CustomerSessionManager.getPhone(requireContext())
            if (phone.isNullOrBlank()) {
                Toast.makeText(requireContext(), localized(R.string.phone_required), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val name = etName.text?.toString()?.trim().orEmpty()
            val age = etAge.text?.toString()?.trim().orEmpty()
            if (name.isBlank() || age.isBlank() || selectedTests.isEmpty() || selectedDate == null || selectedSlot == null) {
                Toast.makeText(requireContext(), localized(R.string.fill_all_fields), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val total = selectedTests.values.sumOf { it.rate }
            pendingAdvance = total * 0.5
            PaymentManager(requireActivity(), this).startPayment(
                amount = pendingAdvance,
                name = name,
                email = CustomerSessionManager.getEmail(requireContext()).orEmpty(),
                phone = phone,
                description = localized(R.string.payment_description_prebook),
                orderNote = "PREBOOK $selectedDate $selectedSlot",
            )
        }
    }

    private fun loadCalendar(
        progress: ProgressBar,
        spinnerDate: AutoCompleteTextView,
        spinnerSlot: AutoCompleteTextView,
        tvPolicy: TextView,
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            progress.visibility = View.VISIBLE
            customerRepo.getPrebookCalendar().fold(
                onSuccess = { calendar ->
                    tvPolicy.text = localized(R.string.prebook_policy, calendar.reschedulePhone)
                    bindCalendar(calendar, spinnerDate, spinnerSlot)
                    progress.visibility = View.GONE
                },
                onFailure = {
                    progress.visibility = View.GONE
                    Toast.makeText(requireContext(), localized(R.string.network_error), Toast.LENGTH_LONG).show()
                },
            )
        }
    }

    private fun bindCalendar(
        calendar: PrebookCalendar,
        spinnerDate: AutoCompleteTextView,
        spinnerSlot: AutoCompleteTextView,
    ) {
        val dateLabels = calendar.dates.map { it.date }
        spinnerDate.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, dateLabels),
        )
        spinnerDate.setOnItemClickListener { _, _, pos, _ ->
            val date = calendar.dates[pos]
            selectedDate = date.date
            selectedSlot = null
            spinnerSlot.setText("", false)
            val available = date.slots.filter { it.available }
            val labels = available.map { localized(R.string.slots_remaining, it.label, it.remaining) }
            spinnerSlot.setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, labels),
            )
            spinnerSlot.setOnItemClickListener { _, _, slotPos, _ ->
                selectedSlot = available[slotPos].timeSlot
            }
        }
    }

    private fun searchTests(query: String, progress: ProgressBar) {
        viewLifecycleOwner.lifecycleScope.launch {
            progress.visibility = View.VISIBLE
            aktivRepo.searchTestsCached(requireContext(), query).fold(
                onSuccess = {
                    testAdapterItems = it
                    testAdapter.submit(it, selectedTests.values.toList())
                    progress.visibility = View.GONE
                },
                onFailure = {
                    progress.visibility = View.GONE
                    Toast.makeText(requireContext(), localized(R.string.network_error), Toast.LENGTH_SHORT).show()
                },
            )
        }
    }

    private fun updateTotals(tvSelected: TextView, tvTotal: TextView, tvAdvance: TextView) {
        val names = selectedTests.values.joinToString(", ") { it.testName }
        val total = selectedTests.values.sumOf { it.rate }
        tvSelected.text = names.ifBlank { localized(R.string.search_tests) }
        tvTotal.text = localized(R.string.total_amount_value, total)
        tvAdvance.text = localized(R.string.advance_amount_value, total * 0.5)
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?) {
        val paymentId = razorpayPaymentId?.takeIf { it.isNotBlank() }
        if (paymentId == null) {
            Toast.makeText(requireContext(), localized(R.string.payment_failed), Toast.LENGTH_LONG).show()
            return
        }
        val root = view ?: return
        val phone = CustomerSessionManager.getPhone(requireContext()) ?: return
        // If the fragment/activity was recreated during Razorpay checkout, the transient
        // selection can be lost. Never submit a corrupt ₹0 booking with empty tests/slot —
        // surface the payment id so the paid user can reconcile with support.
        if (selectedTests.isEmpty() || selectedDate.isNullOrBlank() || selectedSlot.isNullOrBlank() || pendingAdvance <= 0.0) {
            Toast.makeText(
                requireContext(),
                localized(R.string.payment_recorded_contact_support, paymentId),
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        val etName = root.findViewById<TextInputEditText>(R.id.etPatientName)
        val etAge = root.findViewById<TextInputEditText>(R.id.etAgeYear)
        val spinnerSex = root.findViewById<AutoCompleteTextView>(R.id.spinnerSex)
        val progress = root.findViewById<ProgressBar>(R.id.progressBar)
        viewLifecycleOwner.lifecycleScope.launch {
            progress.visibility = View.VISIBLE
            val request = CustomerPrebookRequest(
                patientName = etName.text?.toString()?.trim().orEmpty(),
                phone = phone,
                sex = spinnerSex.text?.toString() ?: getString(R.string.sex_male),
                ageYear = etAge.text?.toString()?.toIntOrNull(),
                testKeys = selectedTests.keys.toList(),
                slotDate = selectedDate.orEmpty(),
                timeSlot = selectedSlot.orEmpty(),
                paymentId = paymentId,
                amountPaid = pendingAdvance,
                email = CustomerSessionManager.getEmail(requireContext()),
            )
            customerRepo.createPrebook(request).fold(
                onSuccess = { response ->
                    progress.visibility = View.GONE
                    Toast.makeText(
                        requireContext(),
                        localized(R.string.prebook_success, response.alcCode, response.reschedulePhone),
                        Toast.LENGTH_LONG,
                    ).show()
                    selectedTests.clear()
                    // Reflect the cleared selection in the UI (totals + list) instead of
                    // leaving the old amounts on screen after a successful booking.
                    testAdapter.submit(testAdapterItems, emptyList())
                    updateTotals(
                        root.findViewById(R.id.tvSelectedTests),
                        root.findViewById(R.id.tvTotalAmount),
                        root.findViewById(R.id.tvAdvanceAmount),
                    )
                },
                onFailure = { error ->
                    progress.visibility = View.GONE
                    Toast.makeText(
                        requireContext(),
                        error.message ?: localized(R.string.something_went_wrong),
                        Toast.LENGTH_LONG,
                    ).show()
                },
            )
        }
    }

    override fun onPaymentError(code: Int, description: String?) {
        Toast.makeText(
            requireContext(),
            description ?: localized(R.string.payment_failed),
            Toast.LENGTH_LONG,
        ).show()
    }

    override fun onDestroyView() {
        (activity as? MainActivity)?.setPaymentListener(null)
        searchJob?.cancel()
        super.onDestroyView()
    }
}
