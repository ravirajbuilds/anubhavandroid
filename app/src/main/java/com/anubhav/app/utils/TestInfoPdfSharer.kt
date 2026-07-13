package com.anubhav.app.utils

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.anubhav.app.R
import com.anubhav.app.data.repository.TestInfoRepository
import java.io.File
import java.io.FileOutputStream

/**
 * Renders the test-display content (reference range, entered value/stage and the
 * bilingual what/why/improve/further-tests sections) to a shareable PDF.
 */
object TestInfoPdfSharer {

    fun share(context: Context, resolved: TestInfoRepository.Resolved, isBengali: Boolean, value: Double?) {
        val file = build(context, resolved, isBengali, value)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Anubhav Life Care — ${resolved.testName}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.localized(R.string.share_pdf)))
    }

    private fun pick(isBn: Boolean, en: String, bn: String) = if (isBn && bn.isNotBlank()) bn else en

    private fun build(context: Context, resolved: TestInfoRepository.Resolved, isBn: Boolean, value: Double?): File {
        val dir = File(context.filesDir, "test_info").apply { mkdirs() }
        val safe = resolved.testName.replace(Regex("[^A-Za-z0-9_-]"), "_").take(48)
        val file = File(dir, "test_${safe}.pdf")

        val doc = PdfDocument()
        val w = Doc(doc)
        try {
            w.heading("Anubhav Life Care", 20f)
            w.heading(resolved.testName, 16f)
            val aka = pick(isBn, resolved.specific?.akaEn.orEmpty(), resolved.specific?.akaBn.orEmpty())
            if (aka.isNotBlank()) w.body(aka)
            w.gap()

            val spec = resolved.specific
            if (spec != null && spec.numeric && spec.ranges.isNotEmpty()) {
                val range = resolved.rangeFor(null)
                if (range != null) {
                    w.label(context.localized(R.string.reference_range, "${fmt(range.low)}–${fmt(range.high)} ${spec.unit}".trim()))
                    if (value != null) {
                        val stageId = resolved.stageIdFor(value, range)
                        val stage = resolved.stage(stageId)
                        val label = stage?.let { pick(isBn, it.labelEn, it.labelBn) }?.takeIf { it.isNotBlank() }
                            ?: defaultStageLabel(context, stageId)
                        w.label("${context.localized(R.string.enter_your_value)}: ${fmt(value)} ${spec.unit}  —  $label")
                        val exp = stage?.let { pick(isBn, it.explainerEn, it.explainerBn) }.orEmpty()
                        if (exp.isNotBlank()) w.body(exp)
                    }
                    w.gap()
                }
            }

            val g = resolved.generic
            section(context, w, R.string.section_what, isBn, spec?.whatEn.orEmpty(), spec?.whatBn.orEmpty(), g?.whatEn.orEmpty(), g?.whatBn.orEmpty())
            section(context, w, R.string.section_why, isBn, spec?.whyEn.orEmpty(), spec?.whyBn.orEmpty(), g?.whyEn.orEmpty(), g?.whyBn.orEmpty())
            section(context, w, R.string.section_improve, isBn, spec?.improveEn.orEmpty(), spec?.improveBn.orEmpty(), g?.improveEn.orEmpty(), g?.improveBn.orEmpty())
            section(context, w, R.string.section_more, isBn, spec?.moreEn.orEmpty(), spec?.moreBn.orEmpty(), g?.moreEn.orEmpty(), g?.moreBn.orEmpty())

            w.gap()
            w.small(context.localized(R.string.metrics_disclaimer))
            w.finish()
            FileOutputStream(file).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return file
    }

    private fun section(context: Context, w: Doc, titleRes: Int, isBn: Boolean, spEn: String, spBn: String, gEn: String, gBn: String) {
        val body = pick(isBn, spEn.ifBlank { gEn }, spBn.ifBlank { gBn })
        if (body.isBlank()) return
        w.label(context.localized(titleRes))
        w.body(body)
        w.gap()
    }

    private fun defaultStageLabel(context: Context, stageId: String) = when (stageId) {
        "low" -> context.localized(R.string.stage_low)
        "high" -> context.localized(R.string.stage_high)
        else -> context.localized(R.string.stage_normal)
    }

    private fun fmt(d: Double) =
        if (d == d.toLong().toDouble()) d.toLong().toString() else "%.2f".format(d).trimEnd('0').trimEnd('.')

    /** Minimal paginating text writer over a PdfDocument (A4 @ 72dpi). */
    private class Doc(private val doc: PdfDocument) {
        private val left = 48f
        private val right = 547f
        private val top = 56f
        private val bottom = 800f
        private var pageNo = 0
        private var page: PdfDocument.Page = newPage()
        private var canvas: Canvas = page.canvas
        private var y = top

        private val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; color = 0xFF146C94.toInt(); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; color = 0xFF222222.toInt() }
        private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = 0xFF888888.toInt() }

        private fun newPage(): PdfDocument.Page {
            pageNo += 1
            return doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
        }

        private fun ensure(space: Float) {
            if (y + space <= bottom) return
            doc.finishPage(page)
            page = newPage()
            canvas = page.canvas
            y = top
        }

        fun heading(text: String, size: Float) {
            headingPaint.textSize = size
            ensure(size + 8f)
            canvas.drawText(text, left, y + size, headingPaint)
            y += size + 10f
        }

        fun label(text: String) = paragraph(text, labelPaint, 16f)
        fun body(text: String) = paragraph(text, bodyPaint, 15f)
        fun small(text: String) = paragraph(text, smallPaint, 13f)
        fun gap() { y += 8f }

        private fun paragraph(text: String, paint: Paint, lineHeight: Float) {
            for (raw in text.split("\n")) {
                for (line in wrap(raw, paint, right - left)) {
                    ensure(lineHeight)
                    canvas.drawText(line, left, y + lineHeight - 3f, paint)
                    y += lineHeight
                }
            }
        }

        private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
            if (text.isBlank()) return listOf("")
            val words = text.split(" ")
            val lines = mutableListOf<String>()
            var cur = StringBuilder()
            for (word in words) {
                val candidate = if (cur.isEmpty()) word else "$cur $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    cur = StringBuilder(candidate)
                } else {
                    if (cur.isNotEmpty()) lines.add(cur.toString())
                    cur = StringBuilder(word)
                }
            }
            if (cur.isNotEmpty()) lines.add(cur.toString())
            return lines.ifEmpty { listOf("") }
        }

        fun finish() { doc.finishPage(page) }
    }
}
