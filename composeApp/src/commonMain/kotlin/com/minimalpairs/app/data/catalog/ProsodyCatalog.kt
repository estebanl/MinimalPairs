package com.minimalpairs.app.data.catalog

import com.minimalpairs.app.core.model.ProsodyPattern
import com.minimalpairs.app.core.model.SyllableInfo
import com.minimalpairs.app.core.model.WeakFormInfo

object ProsodyCatalog {

    val patterns: List<ProsodyPattern> = listOf(
        ProsodyPattern(
            id = "ship_or_sheep_question",
            sentenceText = "Is that a ship or a sheep?",
            contrastId = "i_vs_I",
            patternType = "ALTERNATIVE_QUESTION",
            description = "Rise on the first alternative ('ship' ↗), followed by a decisive fall on the second ('sheep' ↘).",
            syllables = listOf(
                SyllableInfo("Is", isStressed = false, ipa = "ɪz"),
                SyllableInfo("that", isStressed = false, ipa = "ðæt"),
                SyllableInfo("a", isStressed = false, isWeakForm = true, ipa = "ə"),
                SyllableInfo("ship", isStressed = true, ipa = "ʃɪp", targetF0Hz = 245f),
                SyllableInfo("or", isStressed = false, isWeakForm = true, ipa = "ə"),
                SyllableInfo("a", isStressed = false, isWeakForm = true, ipa = "ə"),
                SyllableInfo("sheep", isStressed = true, ipa = "ʃiːp", targetF0Hz = 150f)
            ),
            referenceContour = listOf(
                0.00f to 180f,
                0.20f to 195f,
                0.45f to 255f, // Peak rise on 'ship'
                0.60f to 200f,
                0.75f to 210f,
                1.00f to 140f  // Terminal fall on 'sheep'
            ),
            weakForms = listOf(
                WeakFormInfo("a", "ə", "Reduced to schwa /ə/, under 70ms."),
                WeakFormInfo("or", "ə", "Reduced before consonants.")
            ),
            linkingFeatures = listOf(
                "Consonant-to-vowel link: 'Is that a' -> /ɪz-ðæ-tə/"
            )
        ),
        ProsodyPattern(
            id = "pen_and_pan_statement",
            sentenceText = "He bought a pen and a pan.",
            contrastId = "ae_vs_e",
            patternType = "LIST_INTONATION",
            description = "Continuing rise on the first item ('pen' ↗), terminal fall on the last ('pan' ↘).",
            syllables = listOf(
                SyllableInfo("He", isStressed = false, ipa = "hi"),
                SyllableInfo("bought", isStressed = true, ipa = "bɔːt", targetF0Hz = 200f),
                SyllableInfo("a", isStressed = false, isWeakForm = true, ipa = "ə"),
                SyllableInfo("pen", isStressed = true, ipa = "pen", targetF0Hz = 230f),
                SyllableInfo("and", isStressed = false, isWeakForm = true, ipa = "ən"),
                SyllableInfo("a", isStressed = false, isWeakForm = true, ipa = "ə"),
                SyllableInfo("pan", isStressed = true, ipa = "pæn", targetF0Hz = 145f)
            ),
            referenceContour = listOf(
                0.00f to 175f,
                0.25f to 210f,
                0.50f to 235f, // Rise on 'pen'
                0.65f to 185f,
                0.80f to 195f,
                1.00f to 135f  // Fall on 'pan'
            ),
            weakForms = listOf(
                WeakFormInfo("and", "ən", "Drop 'd', reduce vowel to schwa."),
                WeakFormInfo("a", "ə", "Quick unstressed schwa.")
            ),
            linkingFeatures = listOf(
                "Linking /d/ to schwa in 'and a' -> /ə-nə/"
            )
        ),
        ProsodyPattern(
            id = "full_you_fool",
            sentenceText = "Are you full, you fool?",
            contrastId = "u_vs_uu",
            patternType = "POLITE_INQUIRY_VS_TAG",
            description = "Rising inflection on 'full' (yes/no question), falling tag on 'fool'.",
            syllables = listOf(
                SyllableInfo("Are", isStressed = false, isWeakForm = true, ipa = "ə"),
                SyllableInfo("you", isStressed = false, ipa = "jʊ"),
                SyllableInfo("full", isStressed = true, ipa = "fʊl", targetF0Hz = 245f),
                SyllableInfo("you", isStressed = false, ipa = "jʊ"),
                SyllableInfo("fool", isStressed = true, ipa = "fuːl", targetF0Hz = 150f)
            ),
            referenceContour = listOf(
                0.00f to 170f,
                0.25f to 190f,
                0.50f to 250f, // High rise on 'full'
                0.70f to 190f,
                1.00f to 140f  // Low fall on 'fool'
            ),
            weakForms = listOf(
                WeakFormInfo("are", "ə", "Unstressed auxiliary weak form.")
            ),
            linkingFeatures = listOf(
                "Smooth glide from vowel to vowel: 'Are you' -> /ə-juː/"
            )
        )
    )

    fun getPatternById(id: String): ProsodyPattern? = patterns.firstOrNull { it.id == id }
}
