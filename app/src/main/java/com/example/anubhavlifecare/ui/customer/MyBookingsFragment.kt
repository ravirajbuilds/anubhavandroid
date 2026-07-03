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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.anubhavlifecare.R
import com.example.anubhavlifecare.data.model.CustomerBill
import com.example.anubhavlifecare.data.repository.CustomerRepository
import com.example.anubhavlifecare.utils.CustomerSessionManager
import com.example.anubhavlifecare.utils.localized
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class MyBookingsFragment : Fragment() {
    private val repo = CustomerRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_customer_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = view.findViewById<RecyclerView>(R.id.rvItems)
        val progress = view.findViewById<ProgressBar>(R.id.progressBar)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        val btnEmpty = view.findViewById<MaterialButton>(R.id.btnEmptyAction)
        view.findViewById<TextView>(R.id.tvTitle).text = localized(R.string.menu_my_bookings)
        btnEmpty.text = localized(R.string.book_a_test)

        val phone = CustomerSessionManager.getPhone(requireContext())
        if (phone.isNullOrBlank()) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = localized(R.string.phone_required)
            btnEmpty.visibility = View.GONE
            return
        }

        btnEmpty.setOnClickListener { findNavController().navigate(R.id.nav_book_test) }

        rv.layoutManager = LinearLayoutManager(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            progress.visibility = View.VISIBLE
            repo.getBills(phone).fold(
                onSuccess = { bills ->
                    progress.visibility = View.GONE
                    if (bills.isEmpty()) {
                        tvEmpty.visibility = View.VISIBLE
                        tvEmpty.text = localized(R.string.no_bookings_hint)
                        btnEmpty.visibility = View.VISIBLE
                    } else {
                        rv.adapter = BillAdapter(bills, requireContext())
                    }
                },
                onFailure = {
                    progress.visibility = View.GONE
                    tvEmpty.visibility = View.VISIBLE
                    tvEmpty.text = localized(R.string.network_error)
                    Toast.makeText(requireContext(), it.message, Toast.LENGTH_LONG).show()
                },
            )
        }
    }
}

private class BillAdapter(
    private val items: List<CustomerBill>,
    private val context: android.content.Context,
) : RecyclerView.Adapter<BillAdapter.VH>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context).apply { setPadding(24, 24, 24, 24) }
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val b = items[position]
        holder.text.text = context.localized(
            R.string.bill_summary,
            b.billNo ?: "—",
            b.billDate ?: "—",
            b.netAmount,
            b.receivedAmount,
            b.pendingAmount,
        )
        b.rescheduleNote?.let { holder.text.append("\n$it") }
    }

    override fun getItemCount() = items.size
    class VH(val text: TextView) : RecyclerView.ViewHolder(text)
}
