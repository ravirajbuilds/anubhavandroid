package com.anubhav.app.ui.customer

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.anubhav.app.data.model.CustomerVisit
import com.anubhav.app.data.repository.CustomerRepository
import com.anubhav.app.utils.CustomerSessionManager
import com.anubhav.app.utils.ReportFetcher
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/**
 * My Reports — when logged in, lists EVERY AKTIV visit under the patient's phone
 * (names may differ: relatives share a number) from the static all-history DB.
 * PDFs are fetched (collated into one) + cached only when "View Report" is tapped.
 * A "Fetch another report" option below covers reports under a different number/bill.
 */
class MyReportsFragment : Fragment() {
    private val repo = CustomerRepository()
    private lateinit var root: LinearLayout
    private lateinit var listContainer: LinearLayout
    private lateinit var statusView: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        val scroll = ScrollView(requireContext()).apply { setBackgroundColor(0xFFF9FAFB.toInt()) }
        root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        scroll.addView(root, ViewGroup.LayoutParams(MATCH, WRAP))
        return scroll
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        root.addView(TextView(requireContext()).apply {
            text = "My Reports"; textSize = 20f; setTextColor(0xFF0D9488.toInt())
            setTypeface(typeface, Typeface.BOLD)
        })
        statusView = TextView(requireContext()).apply {
            setTextColor(0xFF6B7280.toInt()); textSize = 13f; setPadding(0, dp(6), 0, dp(6))
        }
        root.addView(statusView)
        listContainer = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listContainer)

        val phone = CustomerSessionManager.getPhone(requireContext()).orEmpty()
        if (phone.isBlank()) {
            statusView.text = "Please log in to see your reports."
            addFetchOtherButton()
        } else {
            loadHistory(phone)
        }
    }

    private fun loadHistory(phone: String) {
        statusView.text = "Loading your reports…"
        listContainer.removeAllViews()
        viewLifecycleOwner.lifecycleScope.launch {
            repo.getHistory(phone).fold(
                onSuccess = { res ->
                    listContainer.removeAllViews()
                    if (res.visits.isEmpty()) {
                        statusView.text = "No reports found for this number."
                    } else {
                        statusView.text = "${res.visits.size} visit(s) linked to your number"
                        res.visits.forEach { listContainer.addView(visitCard(it)) }
                    }
                    addFetchOtherButton()
                },
                onFailure = {
                    statusView.text = "Reports are temporarily unavailable (offline 1–6 AM). Please try again."
                    addFetchOtherButton()
                },
            )
        }
    }

    /** One visit row: PATIENT NAME (left) — Date (right); below: ALC + tests; right: View Report. */
    private fun visitCard(v: CustomerVisit): View {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) }
        }
        // top row: name (left) + date (right)
        val topRow = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        topRow.addView(TextView(requireContext()).apply {
            text = (v.patientName ?: "").trim().ifBlank { "Patient" }
            setTextColor(0xFF111111.toInt()); textSize = 15f; setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        })
        topRow.addView(TextView(requireContext()).apply {
            text = v.billDate ?: ""; setTextColor(0xFF6B7280.toInt()); textSize = 13f
        })
        card.addView(topRow)

        // bottom row: ALC + tests (small black) on left, View Report on right
        val botRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, 0)
        }
        val info = TextView(requireContext()).apply {
            val alc = v.billNo ?: ""
            val tests = v.tests ?: ""
            text = if (tests.isBlank()) alc else "$alc\n$tests"
            setTextColor(Color.BLACK); textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        botRow.addView(info)

        val viewBtn = Button(requireContext()).apply {
            text = "View Report"; isAllCaps = false
            setOnClickListener { onViewReport(v, this) }
            isEnabled = v.hasViewLink
            alpha = if (v.hasViewLink) 1f else 0.5f
        }
        botRow.addView(viewBtn)
        card.addView(botRow)
        if (!v.hasViewLink) {
            card.addView(TextView(requireContext()).apply {
                text = "Report not ready / awaiting authorisation"
                setTextColor(0xFFB45309.toInt()); textSize = 11f; setPadding(0, dp(4), 0, 0)
            })
        }
        return card
    }

    private fun onViewReport(v: CustomerVisit, btn: Button) {
        val link = v.viewLink
        if (link.isNullOrBlank()) {
            Toast.makeText(requireContext(), "This report isn't ready yet.", Toast.LENGTH_SHORT).show()
            return
        }
        btn.isEnabled = false
        val original = btn.text
        btn.text = if (ReportFetcher.isCached(requireContext(), v.billKey)) "Opening…" else "Fetching…"
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { ReportFetcher.download(requireContext(), v.billKey, link) }
                .onSuccess { file ->
                    btn.isEnabled = true; btn.text = original
                    runCatching { ReportFetcher.open(requireContext(), file) }
                        .onFailure { Toast.makeText(requireContext(), "No PDF viewer found.", Toast.LENGTH_LONG).show() }
                }
                .onFailure {
                    btn.isEnabled = true; btn.text = original
                    Toast.makeText(requireContext(), "Couldn't fetch the report. Try again (server is offline 1–6 AM).", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun addFetchOtherButton() {
        listContainer.addView(Button(requireContext()).apply {
            text = "Fetch another report"; isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(18) }
            setOnClickListener { showFetchOtherDialog() }
        })
    }

    /** 2-of-3 verification for a report under a DIFFERENT number/bill (relative etc.). */
    private fun showFetchOtherDialog() {
        val ctx = requireContext()
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val etName = EditText(ctx).apply { hint = "Patient name"; inputType = InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_CLASS_TEXT }
        val etPhone = EditText(ctx).apply { hint = "Phone number"; inputType = InputType.TYPE_CLASS_PHONE }
        val etBill = EditText(ctx).apply { hint = "Bill number (after YYMM/ALC/)"; inputType = InputType.TYPE_CLASS_NUMBER }
        var billDateIso: String? = null
        val dateBtn = Button(ctx).apply {
            text = "Pick bill date"; isAllCaps = false
            setOnClickListener {
                val c = Calendar.getInstance()
                DatePickerDialog(ctx, { _, y, m, d ->
                    billDateIso = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                    text = String.format(Locale.US, "Bill date: %02d/%02d/%04d", d, m + 1, y)
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        box.addView(etName); box.addView(etPhone)
        box.addView(TextView(ctx).apply { text = "Bill number"; setPadding(0, dp(8), 0, 0) })
        box.addView(etBill)
        box.addView(TextView(ctx).apply { text = "— OR —"; gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(6)) })
        box.addView(dateBtn)

        val dialog = AlertDialog.Builder(ctx)
            .setTitle("Fetch another report")
            .setMessage("Enter any two of: name, bill number/date, phone.")
            .setView(box)
            .setPositiveButton("Find reports", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = etName.text.toString().trim()
                val phone = etPhone.text.toString().trim()
                val bill = etBill.text.toString().trim()
                val provided = listOf(name.isNotEmpty(), bill.isNotEmpty() || billDateIso != null, phone.isNotEmpty()).count { it }
                if (provided < 2) { Toast.makeText(ctx, "Fill at least two fields.", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                viewLifecycleOwner.lifecycleScope.launch {
                    repo.verify(name, phone, bill, billDateIso).fold(
                        onSuccess = { r ->
                            if (r.matched && r.phone.isNotBlank()) {
                                dialog.dismiss()
                                Toast.makeText(ctx, "Showing reports for ${r.patientName}", Toast.LENGTH_SHORT).show()
                                loadHistory(r.phone)   // show that person's full history
                            } else {
                                Toast.makeText(ctx, "Details didn't match. Check name, bill no/date and phone.", Toast.LENGTH_LONG).show()
                            }
                        },
                        onFailure = { Toast.makeText(ctx, "Service unavailable, try again.", Toast.LENGTH_LONG).show() },
                    )
                }
            }
        }
        dialog.show()
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
