package com.anubhav.app.ui.metrics

import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anubhav.app.R
import com.anubhav.app.data.repository.CatalogRepository
import com.anubhav.app.data.repository.CatalogRepository.CatalogItem
import com.anubhav.app.utils.localized
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Metrics / Health Library — a searchable list of every catalog test. Tapping a
 * test opens its display screen (interactive dial + bilingual explainers).
 */
class MetricsFragment : Fragment() {

    private val adapter = TestListAdapter { item ->
        findNavController().navigate(
            R.id.nav_test_detail,
            bundleOf(
                TestDetailFragment.ARG_TEST_NAME to item.name,
                TestDetailFragment.ARG_CATEGORY to item.category,
            ),
        )
    }
    private var searchJob: Job? = null
    private var lastQuery: String = ""
    private lateinit var statusView: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF4F7F8.toInt())
            setPadding(dp(16), dp(14), dp(16), 0)
        }
        root.addView(TextView(requireContext()).apply {
            text = localized(R.string.menu_metrics)
            textSize = 20f
            setTextColor(0xFF0B2F44.toInt())
            setTypeface(typeface, Typeface.BOLD)
        })
        val search = EditText(requireContext()).apply {
            hint = localized(R.string.metrics_search_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) }
        }
        root.addView(search)
        statusView = TextView(requireContext()).apply {
            textSize = 12f
            setTextColor(0xFF5C6E78.toInt())
            setPadding(0, dp(8), 0, dp(6))
        }
        root.addView(statusView)
        val rv = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@MetricsFragment.adapter
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        }
        root.addView(rv)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(cs: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(cs: CharSequence?, a: Int, b: Int, c: Int) {
                searchJob?.cancel()
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(200)
                    runSearch(cs?.toString().orEmpty())
                }
            }
            override fun afterTextChanged(e: Editable?) = Unit
        })

        runSearch("")
        syncCatalog()
        return root
    }

    private fun runSearch(query: String) {
        // Resolve the context on the main thread and hand the application context to
        // the repository: requireContext() from a background thread throws the moment
        // the fragment is detached, which a search in flight during navigation would hit.
        val ctx = requireContext().applicationContext
        lastQuery = query
        statusView.text = localized(R.string.loading)
        viewLifecycleOwner.lifecycleScope.launch {
            val results = CatalogRepository.search(ctx, query)
            val total = CatalogRepository.matchCount(ctx, query)
            adapter.submit(results)
            statusView.text = if (total > results.size) {
                localized(R.string.metrics_count_capped, results.size, total)
            } else {
                localized(R.string.metrics_count, total)
            }
        }
    }

    /** Refresh the cached catalog in the background; redraw only if it actually changed. */
    private fun syncCatalog() {
        val ctx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            if (CatalogRepository.sync(ctx)) runSearch(lastQuery)
        }
    }

    override fun onDestroyView() {
        searchJob?.cancel()
        super.onDestroyView()
    }

    private inner class TestListAdapter(
        private val onClick: (CatalogItem) -> Unit,
    ) : RecyclerView.Adapter<TestListAdapter.VH>() {
        private var items: List<CatalogItem> = emptyList()

        fun submit(list: List<CatalogItem>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(0xFFFFFFFF.toInt())
                setPadding(dp(14), dp(12), dp(14), dp(12))
                layoutParams = RecyclerView.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(8) }
            }
            val textBox = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            }
            val name = TextView(parent.context).apply {
                textSize = 15f
                setTextColor(0xFF17272F.toInt())
                setTypeface(typeface, Typeface.BOLD)
            }
            val cat = TextView(parent.context).apply {
                textSize = 12f
                setTextColor(0xFF5C6E78.toInt())
            }
            textBox.addView(name)
            textBox.addView(cat)
            row.addView(textBox)
            row.addView(TextView(parent.context).apply {
                text = "›"
                textSize = 22f
                setTextColor(0xFF9AA7AE.toInt())
            })
            return VH(row, name, cat)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.name.text = item.name
            holder.category.text = item.category
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = items.size

        inner class VH(row: View, val name: TextView, val category: TextView) :
            RecyclerView.ViewHolder(row)
    }

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
