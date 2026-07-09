package com.anubhav.app.ui.customer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anubhav.app.R
import com.anubhav.app.data.model.CustomerReport
import com.anubhav.app.data.repository.CustomerRepository
import com.anubhav.app.utils.CustomerSessionManager
import com.anubhav.app.utils.ReportPdfSharer
import com.anubhav.app.utils.localized
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyReportsFragment : Fragment() {
    private val repo = CustomerRepository()
    private lateinit var adapter: ReportAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_customer_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = view.findViewById<RecyclerView>(R.id.rvItems)
        val progress = view.findViewById<View>(R.id.progressBar)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        val btnRefresh = view.findViewById<MaterialButton>(R.id.btnEmptyAction)
        val searchLayout = view.findViewById<TextInputLayout>(R.id.layoutListSearch)
        val search = view.findViewById<TextInputEditText>(R.id.etListSearch)
        val summary = view.findViewById<TextView>(R.id.tvListSummary)

        view.findViewById<TextView>(R.id.tvTitle).text = localized(R.string.menu_my_reports)
        searchLayout.visibility = View.VISIBLE
        searchLayout.hint = localized(R.string.search_reports)
        summary.visibility = View.GONE
        btnRefresh.text = localized(R.string.refresh_from_aktiv)
        btnRefresh.visibility = View.VISIBLE

        adapter = ReportAdapter(
            onShare = { ReportPdfSharer.shareToWhatsApp(requireContext(), it) },
            onPayDue = { openPendingPayments() },
            onSupport = { openReportSupport(it) },
            onVisibleChanged = { reports -> bindSummary(summary, reports) },
        )
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        search.addSearchWatcher { adapter.filter(it) }
        btnRefresh.setOnClickListener { loadReports(progress, tvEmpty, forceRefresh = true) }
        loadReports(progress, tvEmpty, forceRefresh = false)
    }

    private fun loadReports(progress: View, tvEmpty: TextView, forceRefresh: Boolean) {
        val phone = CustomerSessionManager.getPhone(requireContext()).orEmpty()
        if (phone.isBlank()) {
            progress.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = localized(R.string.phone_required)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            progress.visibility = View.VISIBLE
            repo.getReportsCached(requireContext(), phone, forceRefresh).fold(
                onSuccess = { reports ->
                    progress.visibility = View.GONE
                    tvEmpty.visibility = if (reports.isEmpty()) View.VISIBLE else View.GONE
                    tvEmpty.text = localized(R.string.no_reports_hint)
                    adapter.submit(reports)
                    preSaveReadyReports(reports)
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

    private fun bindSummary(summary: TextView, reports: List<CustomerReport>) {
        if (reports.isEmpty()) {
            summary.visibility = View.GONE
            return
        }
        val ready = reports.count { it.isShareable }
        val due = reports.count { it.isBillDueBlocked }
        val pending = (reports.size - ready - due).coerceAtLeast(0)
        summary.visibility = View.VISIBLE
        summary.text = localized(R.string.report_summary, ready, pending, due)
    }

    private fun preSaveReadyReports(reports: List<CustomerReport>) {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            reports.filter { it.isShareable }.forEach {
                runCatching { ReportPdfSharer.ensureSaved(appContext, it) }
            }
        }
    }

    private fun openPendingPayments() {
        runCatching { findNavController().navigate(R.id.nav_pending_payments) }
            .onFailure {
                Toast.makeText(requireContext(), localized(R.string.pending_payments_title), Toast.LENGTH_SHORT).show()
            }
    }

    private fun openReportSupport(report: CustomerReport) {
        val message = localized(
            R.string.report_support_message,
            report.billNo ?: report.billKey.toString(),
            report.testName ?: report.testCode ?: localized(R.string.report_fallback),
        )
        val uri = Uri.parse("https://wa.me/919230755876?text=${Uri.encode(message)}")
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            .onFailure {
                Toast.makeText(requireContext(), localized(R.string.whatsapp_not_available), Toast.LENGTH_SHORT).show()
            }
    }
}

private class ReportAdapter(
    private val onShare: (CustomerReport) -> Unit,
    private val onPayDue: (CustomerReport) -> Unit,
    private val onSupport: (CustomerReport) -> Unit,
    private val onVisibleChanged: (List<CustomerReport>) -> Unit,
) : RecyclerView.Adapter<ReportAdapter.VH>() {
    private var allItems: List<CustomerReport> = emptyList()
    private var items: List<CustomerReport> = emptyList()
    private var query: String = ""

    fun submit(newItems: List<CustomerReport>) {
        allItems = newItems
        applyFilter()
    }

    fun filter(newQuery: String) {
        query = newQuery
        applyFilter()
    }

    private fun applyFilter() {
        val needle = query.trim().lowercase()
        items = if (needle.isBlank()) {
            allItems
        } else {
            allItems.filter {
                listOf(
                    it.patientName,
                    it.billNo,
                    it.billDate,
                    it.testName,
                    it.testCode,
                    it.reportingDate,
                    it.status,
                    it.statusMessage,
                ).joinToString(" ").lowercase().contains(needle)
            }
        }
        notifyDataSetChanged()
        onVisibleChanged(items)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val context = parent.context
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 18, 24, 18)
            background = ContextCompat.getDrawable(context, R.drawable.list_item_background)
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 0, 0, 10) }
        }
        val title = TextView(context).apply {
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        val meta = TextView(context).apply {
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            textSize = 13f
            setLineSpacing(2f, 1f)
        }
        val status = TextView(context).apply {
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            textSize = 12f
            setPadding(14, 8, 14, 8)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isBaselineAligned = false
        }
        val share = actionButton(context, R.string.share_pdf_whatsapp, R.drawable.ic_whatsapp)
        val secondary = actionButton(context, R.string.whatsapp_support, R.drawable.ic_whatsapp)
        actions.addView(share, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 10, 0, 0)
        })
        actions.addView(secondary, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 8, 0, 0)
        })
        row.addView(title)
        row.addView(meta)
        row.addView(status, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, 10, 0, 0) })
        row.addView(actions)
        return VH(row, title, meta, status, share, secondary)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val report = items[position]
        val context = holder.itemView.context
        holder.title.text = report.testName ?: report.testCode ?: context.localized(R.string.menu_my_reports)
        holder.meta.text = buildString {
            report.patientName?.takeIf { it.isNotBlank() }?.let { append(it).append('\n') }
            append(report.billNo ?: context.localized(R.string.bill_key_value, report.billKey))
            report.reportingDate?.takeIf { it.isNotBlank() }?.let { append(" | ").append(it) }
            report.billDate?.takeIf { it.isNotBlank() }?.let { append('\n').append(it) }
        }
        holder.status.text = report.availabilityText(context)
        holder.status.background = ContextCompat.getDrawable(context, report.statusBackground())

        holder.share.isEnabled = report.isShareable
        holder.share.alpha = if (report.isShareable) 1f else 0.45f
        holder.share.setOnClickListener { if (report.isShareable) onShare(report) }

        if (report.isBillDueBlocked) {
            holder.secondary.text = context.localized(R.string.pay_pending_bill)
            holder.secondary.setIconResource(R.drawable.ic_payments)
            holder.secondary.setOnClickListener { onPayDue(report) }
        } else {
            holder.secondary.text = context.localized(R.string.whatsapp_support)
            holder.secondary.setIconResource(R.drawable.ic_whatsapp)
            holder.secondary.setOnClickListener { onSupport(report) }
        }
    }

    override fun getItemCount(): Int = items.size

    class VH(
        itemView: View,
        val title: TextView,
        val meta: TextView,
        val status: TextView,
        val share: MaterialButton,
        val secondary: MaterialButton,
    ) : RecyclerView.ViewHolder(itemView)
}

private fun actionButton(context: Context, textRes: Int, iconRes: Int): MaterialButton =
    MaterialButton(context).apply {
        text = context.localized(textRes)
        setIconResource(iconRes)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        iconPadding = 6
        minHeight = 48
    }

private fun CustomerReport.availabilityText(context: Context): String =
    statusMessage ?: when {
        isBillDueBlocked -> context.localized(R.string.report_blocked_due)
        isShareable -> context.localized(R.string.report_ready_to_share)
        else -> context.localized(R.string.report_not_ready)
    }

private fun CustomerReport.statusBackground(): Int = when {
    isBillDueBlocked -> R.drawable.chip_error_bg
    isShareable -> R.drawable.chip_success_soft_bg
    else -> R.drawable.chip_warning_bg
}

private fun TextInputEditText.addSearchWatcher(onQueryChanged: (String) -> Unit) {
    addTextChangedListener(
        object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                onQueryChanged(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        },
    )
}
