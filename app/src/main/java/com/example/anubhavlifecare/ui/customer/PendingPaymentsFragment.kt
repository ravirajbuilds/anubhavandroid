package com.example.anubhavlifecare.ui.customer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.anubhavlifecare.MainActivity
import com.example.anubhavlifecare.R
import com.example.anubhavlifecare.data.model.CustomerBill
import com.example.anubhavlifecare.data.model.CustomerPaymentRequest
import com.example.anubhavlifecare.data.repository.CustomerRepository
import com.example.anubhavlifecare.utils.CustomerSessionManager
import com.example.anubhavlifecare.utils.PaymentManager
import com.google.android.material.button.MaterialButton
import com.razorpay.PaymentResultListener
import kotlinx.coroutines.launch

class PendingPaymentsFragment : Fragment(), PaymentResultListener {
    private val repo = CustomerRepository()
    private var pendingBill: CustomerBill? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_pending_payments, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? MainActivity)?.setPaymentListener(this)
        val rv = view.findViewById<RecyclerView>(R.id.rvItems)
        val progress = view.findViewById<ProgressBar>(R.id.progressBar)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        val btnRefresh = view.findViewById<MaterialButton>(R.id.btnRefresh)

        val phone = CustomerSessionManager.getPhone(requireContext()) ?: return

        fun load() {
            viewLifecycleOwner.lifecycleScope.launch {
                progress.visibility = View.VISIBLE
                repo.getPendingPayments(phone).fold(
                    onSuccess = { bills ->
                        progress.visibility = View.GONE
                        tvEmpty.visibility = if (bills.isEmpty()) View.VISIBLE else View.GONE
                        rv.layoutManager = LinearLayoutManager(requireContext())
                        rv.adapter = PendingAdapter(bills) { bill ->
                            pendingBill = bill
                            PaymentManager(requireActivity(), this@PendingPaymentsFragment)
                                .startPayment(
                                    amount = bill.pendingAmount,
                                    name = bill.patientName.orEmpty(),
                                    email = CustomerSessionManager.getEmail(requireContext()).orEmpty(),
                                    phone = phone,
                                    description = "Pending bill ${bill.billNo}",
                                )
                        }
                    },
                    onFailure = {
                        progress.visibility = View.GONE
                        Toast.makeText(requireContext(), it.message, Toast.LENGTH_LONG).show()
                    },
                )
            }
        }

        btnRefresh.setOnClickListener { load() }
        load()
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?) {
        val bill = pendingBill ?: return
        val phone = CustomerSessionManager.getPhone(requireContext()) ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            repo.payPending(
                CustomerPaymentRequest(
                    billKey = bill.billKey,
                    phone = phone,
                    amountPaid = bill.pendingAmount,
                    paymentId = razorpayPaymentId ?: "unknown",
                ),
            ).fold(
                onSuccess = {
                    Toast.makeText(requireContext(), R.string.payment_success, Toast.LENGTH_LONG).show()
                    parentFragmentManager.beginTransaction().detach(this@PendingPaymentsFragment).commit()
                    parentFragmentManager.beginTransaction().attach(this@PendingPaymentsFragment).commit()
                },
                onFailure = {
                    Toast.makeText(requireContext(), it.message, Toast.LENGTH_LONG).show()
                },
            )
        }
    }

    override fun onDestroyView() {
        (activity as? MainActivity)?.setPaymentListener(null)
        super.onDestroyView()
    }

    override fun onPaymentError(code: Int, description: String?) {
        Toast.makeText(requireContext(), description, Toast.LENGTH_LONG).show()
    }
}

private class PendingAdapter(
    private val items: List<CustomerBill>,
    private val onPay: (CustomerBill) -> Unit,
) : RecyclerView.Adapter<PendingAdapter.VH>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val btn = MaterialButton(parent.context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(16, 8, 16, 8) }
        }
        return VH(btn)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val b = items[position]
        holder.button.text = "${b.billNo} — Pay ₹${b.pendingAmount}"
        holder.button.setOnClickListener { onPay(b) }
    }

    override fun getItemCount() = items.size
    class VH(val button: MaterialButton) : RecyclerView.ViewHolder(button)
}
