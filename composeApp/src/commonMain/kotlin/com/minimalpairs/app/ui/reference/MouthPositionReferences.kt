package com.minimalpairs.app.ui.reference

import androidx.compose.runtime.Immutable
import minimalpairs.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource

@Immutable
data class MouthPositionReference(
    val assetKey: String,
    val front: DrawableResource,
    val side: DrawableResource,
    val placementCue: String,
    val frontDescription: String,
    val sideDescription: String
)

/**
 * Static, simplified British-English teaching poses, not inferred anatomy.
 * Separate from contrast-level VisualIndicators: a pose belongs to a sound.
 * The catalog's /e/ denotes DRESS, not the cardinal close-mid vowel.
 * Art direction and phonetic references: docs/MOUTH_POSITION_ARTWORK.md.
 */
object MouthPositionReferences {
    val byPhoneme: Map<String, MouthPositionReference> = mapOf(
        "iː" to MouthPositionReference(
            "fleece", Res.drawable.mouth_fleece_front, Res.drawable.mouth_fleece_side,
            "Gently spread your lips. Keep your tongue high and toward the front.",
            "Gently spread, unrounded lips with a narrow opening.",
            "The front of the tongue is high, close to the palate, with the tip behind the lower front teeth."
        ),
        "ɪ" to MouthPositionReference(
            "kit", Res.drawable.mouth_kit_front, Res.drawable.mouth_kit_side,
            "Relax your lips. Keep your tongue near the front, a little lower than in sheep.",
            "Relaxed, unrounded lips with a small opening, less spread than in sheep.",
            "The tongue is near the front, slightly lower and farther back than in sheep."
        ),
        "e" to MouthPositionReference(
            "dress", Res.drawable.mouth_dress_front, Res.drawable.mouth_dress_side,
            "Open your jaw partway. Keep your lips unrounded and your tongue toward the front.",
            "Unrounded lips and a medium mouth opening.",
            "The front of the tongue is below mid height, lower than in ship but higher than in bad."
        ),
        "æ" to MouthPositionReference(
            "trap", Res.drawable.mouth_trap_front, Res.drawable.mouth_trap_side,
            "Lower your jaw. Keep your tongue low and toward the front, with unrounded lips.",
            "Unrounded lips with a wide opening and a lowered jaw.",
            "The tongue is low and toward the front, leaving a large space beneath the palate."
        ),
        "ɑː" to MouthPositionReference(
            "palm", Res.drawable.mouth_palm_front, Res.drawable.mouth_palm_side,
            "Open your jaw wide. Keep your tongue low and toward the back; relax your lips.",
            "Relaxed, unrounded lips with a large opening.",
            "The tongue is low and toward the back, with a wide jaw opening."
        ),
        "ʌ" to MouthPositionReference(
            "strut", Res.drawable.mouth_strut_front, Res.drawable.mouth_strut_side,
            "Relax your lips and open your jaw partway. Let your tongue rest low in the middle.",
            "Relaxed, unrounded lips with a medium opening, smaller than in heart.",
            "The tongue is relaxed, low in the middle of the mouth, with the tip behind the lower front teeth."
        ),
        "ʊ" to MouthPositionReference(
            "foot", Res.drawable.mouth_foot_front, Res.drawable.mouth_foot_side,
            "Round your lips gently. Keep your tongue toward the back, a little lower than in fool.",
            "Loosely rounded lips with a small oval opening.",
            "The tongue is near the back and near the top, but lower and more central than in fool."
        ),
        "uː" to MouthPositionReference(
            "goose", Res.drawable.mouth_goose_front, Res.drawable.mouth_goose_side,
            "Round your lips into a small opening. Raise your tongue toward the back.",
            "Rounded lips project gently forward around a small opening.",
            "The back of the tongue is raised close to the palate, with a small jaw opening."
        ),
        "ɒ" to MouthPositionReference(
            "lot", Res.drawable.mouth_lot_front, Res.drawable.mouth_lot_side,
            "Open your jaw and gently round your lips. Keep your tongue low and toward the back.",
            "Gently rounded lips with a large oval opening and a lowered jaw.",
            "The tongue is low and toward the back, leaving a large space above it."
        ),
        "ɔː" to MouthPositionReference(
            "thought", Res.drawable.mouth_thought_front, Res.drawable.mouth_thought_side,
            "Round your lips. Keep your tongue toward the back, a little higher than in cot.",
            "Rounded lips with a medium oval opening, smaller than in cot.",
            "The back of the tongue is raised partway, higher than in cot, with a moderately open jaw."
        )
    )

    fun forPhoneme(phoneme: String): MouthPositionReference? = byPhoneme[phoneme]
}
