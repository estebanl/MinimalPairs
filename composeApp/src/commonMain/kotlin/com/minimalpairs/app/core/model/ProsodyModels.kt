package com.minimalpairs.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SyllableInfo(
    val text: String,
    val isStressed: Boolean,
    val isWeakForm: Boolean = false,
    val ipa: String,
    val targetF0Hz: Float? = null
)

@Serializable
data class WeakFormInfo(
    val word: String,
    val weakIpa: String,
    val note: String
)

@Serializable
data class ProsodyPattern(
    val id: String,
    val sentenceText: String,
    val contrastId: String,
    val patternType: String,
    val description: String,
    val syllables: List<SyllableInfo>,
    val referenceContour: List<Pair<Float, Float>>,
    val weakForms: List<WeakFormInfo>,
    val linkingFeatures: List<String>
)
