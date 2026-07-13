package com.anubhav.app.ui.metrics

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
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
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.anubhav.app.R
import com.anubhav.app.data.model.TestInfoRange
import com.anubhav.app.data.repository.TestInfoRepository
import com.anubhav.app.utils.LanguageManager
import com.anubhav.app.utils.TestInfoPdfSharer
import com.anubhav.app.utils.localized

/**
 * Test display screen: opened from the Metrics list. Shows a share-PDF action,
 * an interactive reference-range dial with a per-stage explainer, and bilingual
 * "what / why / how to improve / further tests" content for the test.
 */
class TestDetailFragment : Fragment() {

    private lateinit var resolved: TestInfoRepository.Resolved
    private var isBengali = false
    private var currentSex: String? = null
    private var currentValue: Double? = null

    private var gauge: HealthGaugeView? = null
    private var badge: TextView? = null
    private var dynExplainer: TextView? = null
    private var rangeText: TextView? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun pick(en: String, bn: String): String =
        if (isBengali && bn.isNotBlank()) bn else en

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        val scroll = ScrollView(requireContext()).apply { setBackgroundColor(0xFFF4F7F8.toInt()) }
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(28))
        }
        scroll.addView(root, ViewGroup.LayoutParams(MATCH, WRAP))

        isBengali = LanguageManager(requireContext()).isBengali()
        val testName = arguments?.getString(ARG_TEST_NAME).orEmpty().ifBlank { "Test" }
        val category = arguments?.getString(ARG_CATEGORY).orEmpty()
        resolved = TestInfoRepository.resolve(requireContext(), testName, category)

        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title = testName

        buildHeader(root, testName)
        if (resolved.hasGauge) buildGaugeSection(root)
        buildContentSections(root)
        buildDisclaimer(root)
        return scroll
    }

    // --- Header: title + Share PDF ------------------------------------------
    private fun buildHeader(root: LinearLayout, testName: String) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleBox = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        titleBox.addView(TextView(requireContext()).apply {
            text = testName
            textSize = 20f
            setTextColor(0xFF0B2F44.toInt())
            setTypeface(typeface, Typeface.BOLD)
        })
        val aka = pick(resolved.specific?.akaEn.orEmpty(), resolved.specific?.akaBn.orEmpty())
        if (aka.isNotBlank()) {
            titleBox.addView(TextView(requireContext()).apply {
                text = aka
                textSize = 13f
                setTextColor(0xFF5C6E78.toInt())
            })
        }
        row.addView(titleBox)
        row.addView(Button(requireContext()).apply {
            text = localized(R.string.share_pdf)
            isAllCaps = false
            setOnClickListener { onSharePdf() }
        })
        root.addView(row)
    }

    // --- Interactive gauge + stage explainers -------------------------------
    private fun buildGaugeSection(root: LinearLayout) {
        val card = card()
        root.addView(card)

        // Optional Male / Female selector when the range is sex-specific.
        val sexes = resolved.sexes()
        if (sexes.isNotEmpty()) {
            currentSex = sexes.first()
            val sexRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(8))
            }
            val buttons = mutableListOf<Button>()
            sexes.forEach { sex ->
                val b = Button(requireContext()).apply {
                    text = if (sex.equals("female", true)) localized(R.string.sex_female) else localized(R.string.sex_male)
                    isAllCaps = false
                    layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(6) }
                }
                b.setOnClickListener {
                    currentSex = sex
                    buttons.forEach { it.alpha = 0.5f }
                    b.alpha = 1f
                    applyRange()
                }
                buttons.add(b)
                sexRow.addView(b)
            }
            buttons.forEachIndexed { i, b -> b.alpha = if (i == 0) 1f else 0.5f }
            card.addView(sexRow)
        }

        gauge = HealthGaugeView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        card.addView(gauge)

        rangeText = TextView(requireContext()).apply {
            textSize = 13f
            setTextColor(0xFF5C6E78.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(10))
        }
        card.addView(rangeText)

        // Value input.
        card.addView(TextView(requireContext()).apply {
            text = localized(R.string.enter_your_value)
            textSize = 13f
            setTextColor(0xFF17272F.toInt())
            setTypeface(typeface, Typeface.BOLD)
        })
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = resolved.specific?.unit.orEmpty().ifBlank { "0" }
        }
        card.addView(input)

        badge = TextView(requireContext()).apply {
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(12), dp(5), dp(12), dp(5))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(10) }
        }
        card.addView(badge)

        dynExplainer = TextView(requireContext()).apply {
            textSize = 14f
            setTextColor(0xFF17272F.toInt())
            setPadding(0, dp(8), 0, dp(4))
            visibility = View.GONE
        }
        card.addView(dynExplainer)

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                currentValue = s?.toString()?.trim()?.toDoubleOrNull()
                evaluate()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        // Static legend: one line explainer for EACH stage.
        val stages = resolved.specific?.stages.orEmpty()
        if (stages.isNotEmpty()) {
            card.addView(divider())
            card.addView(TextView(requireContext()).apply {
                text = localized(R.string.what_each_zone_means)
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(0xFF0B2F44.toInt())
                setPadding(0, dp(4), 0, dp(6))
            })
            stages.forEach { st ->
                val color = when (st.id.lowercase()) {
                    "low" -> HealthGaugeView.COLOR_LOW
                    "high" -> HealthGaugeView.COLOR_HIGH
                    else -> HealthGaugeView.COLOR_NORMAL
                }
                val line = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(3), 0, dp(3))
                }
                line.addView(TextView(requireContext()).apply {
                    text = "●  "
                    setTextColor(color)
                    textSize = 13f
                })
                line.addView(TextView(requireContext()).apply {
                    val label = pick(st.labelEn, st.labelBn)
                    val exp = pick(st.explainerEn, st.explainerBn)
                    text = if (label.isBlank()) exp else "$label — $exp"
                    textSize = 13f
                    setTextColor(0xFF3A4A52.toInt())
                })
                card.addView(line)
            }
        }

        applyRange()
    }

    private fun applyRange() {
        val spec = resolved.specific ?: return
        val range = resolved.rangeFor(currentSex) ?: return
        gauge?.setRange(spec.gaugeMin, spec.gaugeMax, range.low, range.high, spec.unit)
        val note = pick(range.noteEn, range.noteBn)
        val base = localized(R.string.reference_range, "${fmt(range.low)}–${fmt(range.high)} ${spec.unit}".trim())
        rangeText?.text = if (note.isBlank()) base else "$base  ($note)"
        evaluate()
    }

    private fun evaluate() {
        val v = currentValue
        gauge?.setValue(v)
        if (v == null) {
            badge?.visibility = View.GONE
            dynExplainer?.visibility = View.GONE
            return
        }
        val range = resolved.rangeFor(currentSex)
        val stageId = resolved.stageIdFor(v, range)
        val stage = resolved.stage(stageId)
        val color = gauge?.zoneColorFor(v) ?: HealthGaugeView.COLOR_NORMAL
        val label = stage?.let { pick(it.labelEn, it.labelBn) }?.takeIf { it.isNotBlank() }
            ?: defaultStageLabel(stageId)
        badge?.apply {
            text = label
            background = pill(color)
            visibility = View.VISIBLE
        }
        val exp = stage?.let { pick(it.explainerEn, it.explainerBn) }.orEmpty()
        dynExplainer?.apply {
            text = exp
            visibility = if (exp.isBlank()) View.GONE else View.VISIBLE
        }
    }

    private fun defaultStageLabel(stageId: String): String = when (stageId) {
        "low" -> localized(R.string.stage_low)
        "high" -> localized(R.string.stage_high)
        else -> localized(R.string.stage_normal)
    }

    // --- What / Why / Improve / Further tests --------------------------------
    private fun buildContentSections(root: LinearLayout) {
        val sp = resolved.specific
        val g = resolved.generic
        addSection(root, R.string.section_what,
            sp?.whatEn.orEmpty(), sp?.whatBn.orEmpty(), g?.whatEn.orEmpty(), g?.whatBn.orEmpty())
        addSection(root, R.string.section_why,
            sp?.whyEn.orEmpty(), sp?.whyBn.orEmpty(), g?.whyEn.orEmpty(), g?.whyBn.orEmpty())
        addSection(root, R.string.section_improve,
            sp?.improveEn.orEmpty(), sp?.improveBn.orEmpty(), g?.improveEn.orEmpty(), g?.improveBn.orEmpty())
        addSection(root, R.string.section_more,
            sp?.moreEn.orEmpty(), sp?.moreBn.orEmpty(), g?.moreEn.orEmpty(), g?.moreBn.orEmpty())
    }

    private fun addSection(root: LinearLayout, titleRes: Int, spEn: String, spBn: String, gEn: String, gBn: String) {
        val en = spEn.ifBlank { gEn }
        val bn = spBn.ifBlank { gBn }
        val body = pick(en, bn)
        if (body.isBlank()) return
        val card = card()
        card.addView(TextView(requireContext()).apply {
            text = localized(titleRes)
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFF146C94.toInt())
            setPadding(0, 0, 0, dp(6))
        })
        card.addView(TextView(requireContext()).apply {
            text = body
            textSize = 14f
            setTextColor(0xFF3A4A52.toInt())
            setLineSpacing(dp(2).toFloat(), 1f)
        })
        root.addView(card)
    }

    private fun buildDisclaimer(root: LinearLayout) {
        root.addView(TextView(requireContext()).apply {
            text = localized(R.string.metrics_disclaimer)
            textSize = 11f
            setTextColor(0xFF8A9AA3.toInt())
            setPadding(dp(4), dp(14), dp(4), 0)
        })
    }

    private fun onSharePdf() {
        runCatching {
            TestInfoPdfSharer.share(requireContext(), resolved, isBengali, currentValue, currentSex)
        }.onFailure {
            Toast.makeText(requireContext(), localized(R.string.something_went_wrong), Toast.LENGTH_LONG).show()
        }
    }

    // --- helpers -------------------------------------------------------------
    private fun card(): LinearLayout = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(0xFFFFFFFF.toInt())
        setPadding(dp(14), dp(14), dp(14), dp(14))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) }
    }

    private fun divider(): View = View(requireContext()).apply {
        setBackgroundColor(0xFFE3E9EC.toInt())
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(1)).apply {
            topMargin = dp(10); bottomMargin = dp(4)
        }
    }

    private fun pill(color: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(14).toFloat()
    }

    private fun fmt(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString()
        else "%.2f".format(d).trimEnd('0').trimEnd('.')

    companion object {
        const val ARG_TEST_NAME = "testName"
        const val ARG_CATEGORY = "category"
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
