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
import com.example.anubhavlifecare.R
import com.example.anubhavlifecare.data.model.CustomerReport
import com.example.anubhavlifecare.data.repository.CustomerRepository
import com.example.anubhavlifecare.utils.CustomerSessionManager
import kotlinx.coroutines.launch

class MyReportsFragment : Fragment() {
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
        view.findViewById<TextView>(R.id.tvTitle).text = getString(R.string.menu_my_reports)

        val phone = CustomerSessionManager.getPhone(requireContext()) ?: return

        rv.layoutManager = LinearLayoutManager(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            progress.visibility = View.VISIBLE
            repo.getReports(phone).fold(
                onSuccess = { reports ->
                    progress.visibility = View.GONE
                    if (reports.isEmpty()) tvEmpty.visibility = View.VISIBLE
                    else rv.adapter = ReportAdapter(reports)
                },
                onFailure = {
                    progress.visibility = View.GONE
                    Toast.makeText(requireContext(), it.message, Toast.LENGTH_LONG).show()
                },
            )
        }
    }
}

private class ReportAdapter(private val items: List<CustomerReport>) :
    RecyclerView.Adapter<ReportAdapter.VH>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context).apply { setPadding(24, 24, 24, 24) }
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        holder.text.text = "${r.testName} (${r.billNo}) — ${r.status}\nReport: ${r.reportingDate ?: "—"}"
    }

    override fun getItemCount() = items.size
    class VH(val text: TextView) : RecyclerView.ViewHolder(text)
}
