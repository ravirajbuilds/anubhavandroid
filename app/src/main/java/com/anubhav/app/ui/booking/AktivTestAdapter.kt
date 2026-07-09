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
            ).apply { setMargins(0, 0, 0, resources.getDimensionPixelSize(R.dimen.list_item_gap)) }
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
        val item = items[position]
        val context = holder.card.context
        val isSelected = selected.contains(item.testKey)
        holder.text.text = buildString {
            append(item.testName)
            append('\n')
            append(item.testCode)
            append("  Rs ")
            append("%.2f".format(item.rate))
        }
        holder.card.strokeColor = ContextCompat.getColor(
            context,
            if (isSelected) R.color.accent else R.color.stroke_soft,
        )
        holder.card.setCardBackgroundColor(
            ContextCompat.getColor(
                context,
                if (isSelected) R.color.accent_light else R.color.card_background,
            ),
        )
        holder.card.setOnClickListener { onToggle(item) }
    }

    override fun getItemCount(): Int = items.size

    class VH(val card: MaterialCardView, val text: TextView) : RecyclerView.ViewHolder(card)
}

private fun Int.dp(): Int = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
