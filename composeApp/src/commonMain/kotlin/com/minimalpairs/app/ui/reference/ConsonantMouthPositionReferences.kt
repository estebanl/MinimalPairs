package com.minimalpairs.app.ui.reference

import minimalpairs.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource

/** Voicing pairs share visible placement; each sound keeps its own practice cue. */
internal val consonantMouthPositionReferences: Map<String, MouthPositionReference> = buildMap {
    fun pose(
        key: String,
        front: DrawableResource,
        side: DrawableResource,
        frontDescription: String,
        sideDescription: String,
        vararg phonemes: String
    ) {
        for (phoneme in phonemes) {
            put(phoneme, MouthPositionReference(
                key, front, side, ConsonantPracticeReferences.byPhoneme.getValue(phoneme),
                frontDescription, sideDescription
            ))
        }
    }

    pose(
        "bilabial", Res.drawable.mouth_bilabial_front, Res.drawable.mouth_bilabial_side,
        "Both lips meet to close the mouth before the release.",
        "The lips stop the air while the tongue rests inside the mouth. The illustration shows the closure before release.",
        "p", "b"
    )
    pose(
        "alveolar_stop", Res.drawable.mouth_alveolar_stop_front, Res.drawable.mouth_alveolar_stop_side,
        "The lips are slightly apart; the tongue stays behind the upper front teeth.",
        "The tongue tip touches the ridge behind the upper teeth to stop the air briefly before release.",
        "t", "d"
    )
    pose(
        "velar_stop", Res.drawable.mouth_velar_stop_front, Res.drawable.mouth_velar_stop_side,
        "Relaxed lips are slightly apart; the contact is inside the back of the mouth.",
        "The back of the tongue touches the soft palate to stop the air before release; the tip stays low.",
        "k", "ɡ"
    )
    pose(
        "labiodental", Res.drawable.mouth_labiodental_front, Res.drawable.mouth_labiodental_side,
        "The upper front teeth rest lightly against the lower lip.",
        "The upper incisor edge meets the lower lip, leaving a small gap for air; the tongue is relaxed.",
        "f", "v"
    )
    pose(
        "dental", Res.drawable.mouth_dental_front, Res.drawable.mouth_dental_side,
        "The tongue tip is lightly visible between the upper and lower front teeth.",
        "The tongue tip sits lightly between the incisors with room for air to pass, without a full closure.",
        "θ", "ð"
    )
    pose(
        "alveolar_fricative", Res.drawable.mouth_alveolar_fricative_front, Res.drawable.mouth_alveolar_fricative_side,
        "The lips are gently spread, with the teeth close together and the tongue inside.",
        "The front of the tongue approaches the ridge behind the upper teeth, leaving a narrow channel for air.",
        "s", "z"
    )
    pose(
        "postalveolar_fricative", Res.drawable.mouth_postalveolar_fricative_front, Res.drawable.mouth_postalveolar_fricative_side,
        "The lips are gently rounded and project slightly forward.",
        "The tongue blade rises just behind the tooth ridge, farther back than for /s/, leaving a narrow air gap.",
        "ʃ", "ʒ"
    )
    pose(
        "postalveolar_affricate", Res.drawable.mouth_postalveolar_affricate_front, Res.drawable.mouth_postalveolar_affricate_side,
        "The lips are gently rounded, with the tongue inside behind the tooth ridge.",
        "The tongue blade makes the initial closure behind the tooth ridge, then releases into a fricative. This static view shows the closure stage.",
        "tʃ", "dʒ"
    )
    pose(
        "lateral", Res.drawable.mouth_lateral_front, Res.drawable.mouth_lateral_side,
        "The lips are relaxed and apart, with the tongue tip raised behind the upper front teeth.",
        "The tongue tip touches the tooth ridge. Air passes around the sides of the tongue, outside this central cutaway view.",
        "l"
    )
    pose(
        "rhotic", Res.drawable.mouth_rhotic_front, Res.drawable.mouth_rhotic_side,
        "The lips are slightly rounded and the tongue stays inside the mouth.",
        "The tongue is drawn back toward the palate without touching it. This is one simplified English /r/ teaching pose.",
        "r"
    )
    pose(
        "labiovelar", Res.drawable.mouth_labiovelar_front, Res.drawable.mouth_labiovelar_side,
        "The lips are rounded and project forward around a small opening; the teeth do not touch the lower lip.",
        "The back of the tongue rises toward the soft palate without contact, while the lips round before gliding into the vowel.",
        "w"
    )
}
