package com.minimalpairs.app.ui.reference

/** Placement cues for word-based consonant practice; these are not acoustic measurements. */
object ConsonantPracticeReferences {
    val byPhoneme: Map<String, String> = mapOf(
        "p" to "Close both lips to stop the air, then release them. Keep your throat quiet. Practise the release in the example word rather than adding an extra vowel.",
        "b" to "Close both lips, then release them into the word with voice. Compare the beginning of bear with pear; avoid adding an extra vowel after the consonant.",
        "t" to "Place the tongue tip on the ridge just behind your upper teeth. Stop the air briefly, then release without a voiced buzz.",
        "d" to "Place the tongue tip on the ridge just behind your upper teeth, then release with voice. Keep the tongue movement brief within the word.",
        "k" to "Raise the back of your tongue to the soft palate, briefly blocking the air. Release it without a voiced buzz. Your lips follow the surrounding vowel.",
        "ɡ" to "Raise the back of your tongue to the soft palate, then release with voice. Compare goat with coat while keeping the tongue contact in the same place.",
        "f" to "Touch your upper teeth lightly to your lower lip. Send air through the small gap with a quiet throat; do not close both lips.",
        "v" to "Touch your upper teeth lightly to your lower lip and let air flow with a buzz. Feel the vibration by resting a finger gently on your throat.",
        "θ" to "Bring the tongue tip lightly between your teeth and blow air around it. Keep your throat quiet and avoid stopping the air completely.",
        "ð" to "Bring the tongue tip lightly between your teeth and let air flow with voice. Keep the contact light so the sound does not become /d/.",
        "s" to "Keep your tongue just behind your upper front teeth, leaving a narrow channel for air. Make a steady hiss without a voiced buzz.",
        "z" to "Keep your tongue just behind your upper front teeth and let air flow through a narrow channel. Add voice to the hiss, as in zoo.",
        "ʃ" to "Move the front of your tongue slightly farther back than for /s/. Round your lips gently and let air flow in a quiet, continuous sh sound.",
        "ʒ" to "Use the tongue position and gentle lip rounding of /ʃ/, adding voice. Listen for this sound in the middle of measure.",
        "tʃ" to "Briefly stop the air with the front of your tongue behind the tooth ridge, then release into /ʃ/. Keep the stop and hiss together, as in chair.",
        "dʒ" to "Briefly stop the air behind the tooth ridge, then release into a voiced /ʒ/ sound. Keep the two parts together, as at the start of jam.",
        "l" to "Touch your tongue tip to the ridge behind your upper teeth. Let voiced air pass around the sides of your tongue, as at the start of light.",
        "r" to "For the opening sound in right, draw your tongue back or bunch it without touching the roof of your mouth. Keep the sound voiced and avoid tapping the tongue tip.",
        "w" to "Round your lips and raise the back of your tongue, then glide straight into the vowel. Keep your upper teeth away from your lower lip."
    )

    fun forPhoneme(phoneme: String): String? = byPhoneme[phoneme]
}
