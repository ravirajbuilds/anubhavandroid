package com.anubhav.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * Educational / reference content for a single diagnostic test, used by the
 * Metrics ("Health Library") test-detail screen. Loaded from assets/testInfo.json.
 * All patient-facing copy is bilingual (English + Bengali).
 */
data class TestInfoRange(
    val sex: String = "any",
    val low: Double = 0.0,
    val high: Double = 0.0,
    @SerializedName("note_en") val noteEn: String = "",
    @SerializedName("note_bn") val noteBn: String = "",
)

data class TestInfoStage(
    /** low | normal | high | borderline */
    val id: String = "",
    @SerializedName("label_en") val labelEn: String = "",
    @SerializedName("label_bn") val labelBn: String = "",
    @SerializedName("explainer_en") val explainerEn: String = "",
    @SerializedName("explainer_bn") val explainerBn: String = "",
)

data class TestInfo(
    /** Exact catalog test name (matches testsData.json "name"). */
    val key: String = "",
    val category: String = "",
    @SerializedName("aka_en") val akaEn: String = "",
    @SerializedName("aka_bn") val akaBn: String = "",
    /** true when the test yields a single numeric value shown on a gauge. */
    val numeric: Boolean = false,
    val unit: String = "",
    val gaugeMin: Double = 0.0,
    val gaugeMax: Double = 0.0,
    val ranges: List<TestInfoRange> = emptyList(),
    val stages: List<TestInfoStage> = emptyList(),
    @SerializedName("what_en") val whatEn: String = "",
    @SerializedName("what_bn") val whatBn: String = "",
    @SerializedName("why_en") val whyEn: String = "",
    @SerializedName("why_bn") val whyBn: String = "",
    @SerializedName("improve_en") val improveEn: String = "",
    @SerializedName("improve_bn") val improveBn: String = "",
    @SerializedName("more_en") val moreEn: String = "",
    @SerializedName("more_bn") val moreBn: String = "",
)

/** Generic per-category fallback copy shown when a test has no specific write-up. */
data class TestInfoGeneric(
    val category: String = "",
    @SerializedName("what_en") val whatEn: String = "",
    @SerializedName("what_bn") val whatBn: String = "",
    @SerializedName("why_en") val whyEn: String = "",
    @SerializedName("why_bn") val whyBn: String = "",
    @SerializedName("improve_en") val improveEn: String = "",
    @SerializedName("improve_bn") val improveBn: String = "",
    @SerializedName("more_en") val moreEn: String = "",
    @SerializedName("more_bn") val moreBn: String = "",
)

data class TestInfoDb(
    val version: Int = 1,
    val generic: Map<String, TestInfoGeneric> = emptyMap(),
    val tests: List<TestInfo> = emptyList(),
)
