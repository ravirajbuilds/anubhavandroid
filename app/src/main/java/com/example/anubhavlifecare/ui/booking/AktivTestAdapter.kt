package com.example.anubhavlifecare.ui.booking

import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.anubhavlifecare.data.model.AktivTest

class AktivTestAdapter(
    private val onToggle: (AktivTest) -> Unit,
) : RecyclerView.Adapter<AktivTestAdapter.VH>() {
    private var items: List<AktivTest> = emptyList()
    private var selected: Set<Int> = emptySet()

    fun submit(tests: List<AktivTest>, selectedTests: List<AktivTest> = emptyList()) {
        items = tests
        selected = selectedTests.map { it.testKey }.toSet()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context).apply {
            setPadding(24, 24, 24, 24)
        }
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val test = items[position]
        val checked = selected.contains(test.testKey)
        holder.text.text = "${test.testCode} — ${test.testName} (₹${test.rate})"
        holder.text.alpha = if (checked) 1f else 0.85f
        holder.text.setBackgroundColor(
            if (checked) 0xFFE3F2FD.toInt() else 0x00000000,
        )
        holder.text.setOnClickListener { onToggle(test) }
    }

    override fun getItemCount(): Int = items.size

    class VH(val text: TextView) : RecyclerView.ViewHolder(text)
}
