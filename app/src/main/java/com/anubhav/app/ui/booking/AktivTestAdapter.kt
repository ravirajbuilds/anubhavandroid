package com.anubhav.app.ui.booking

import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.anubhav.app.R
import com.anubhav.app.data.model.AktivTest
import com.google.android.material.card.MaterialCardView

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
        val card = MaterialCardView(parent.context).apply {
            radius = resources.getDimension(R.dimen.list_item_radius)
            cardElevation = 0f
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_background))
            strokeWidth = resources.getDimensionPixelSize(R.dimen.list_item_stroke)
            strokeColor = ContextCompat.getColor(context, R.color.stroke_soft)
            isClickable = true
            isFocusable = true
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(0, 0, 0, resources.getDimensionPixelSize(R.dimen.list_item_gap))
            }
        }

        val text = TextView(parent.context).apply {
            setPadding(18.dp(), 14.dp(), 18.dp(), 14.dp())
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            minHeight = 48.dp()
        }
        card.addView(text)
        return VH(card, text)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val test = items[position]
        val checked = selected.contains(test.testKey)
        holder.text.text = "${test.testCode} - ${test.testName}\nRs ${test.rate}"
        holder.text.alpha = if (checked) 1f else 0.9f
        holder.card.setCardBackgroundColor(
            ContextCompat.getColor(
                holder.card.context,
                if (checked) R.color.secondary_light else R.color.card_background,
            ),
        )
        holder.card.strokeColor = ContextCompat.getColor(
            holder.card.context,
            if (checked) R.color.medical_green else R.color.stroke_soft,
        )
        holder.card.setOnClickListener { onToggle(test) }
    }

    override fun getItemCount(): Int = items.size

    class VH(val card: MaterialCardView, val text: TextView) : RecyclerView.ViewHolder(card)

    private fun Int.dp(): Int = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
