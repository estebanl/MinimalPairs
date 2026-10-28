package com.minimalpairs.app.ui.reference

/** Static articulatory snapshots, not whole-word motion or learner anatomy. */
data class ConsonantModelPhase(val assetKey: String, val label: String, val description: String)
data class ConsonantModelReference(val phases: List<ConsonantModelPhase>, val explanation: String)

/**
 * Original JD3 gestures in the source's /a/ context. English suitability is unreviewed.
 * Voicing pairs share oral geometry: the exported surfaces do not show vocal folds.
 * Provenance and coverage decisions: docs/CONSONANT_3D_ASSETS.md.
 */
object ConsonantModelReferences {
    private fun phase(asset: String, label: String, description: String) = ConsonantModelPhase(asset, label, description)
    private val bilabial = phase("bilabial-closure", "Lip closure", "Both lips meet in this closed snapshot. The sound also requires a release, which is not animated.")
    private val alveolar = phase("alveolar-closure", "Tongue closure", "The tongue tip forms a closure behind the upper teeth. Release is not animated.")
    private val velar = phase("velar-closure", "Back-tongue closure", "The back of the tongue forms a closure toward the soft palate. Release is not animated.")
    private val labiodental = phase("labiodental-fricative", "Lip and teeth", "The lower lip approaches the upper teeth for a labiodental constriction.")
    private val dental = phase("dental-fricative", "Tongue and teeth", "The tongue tip approaches the teeth for a dental constriction.")
    private val sibilant = phase("alveolar-fricative", "Narrow tongue channel", "A narrow constriction is formed near the ridge behind the upper teeth.")
    private val postalveolar = phase("postalveolar-fricative", "Fricative constriction", "The tongue constriction is farther back than the alveolar reference. This snapshot represents the friction phase.")
    private val affricateClosure = phase("postalveolar-closure", "Affricate closure", "The tongue forms the initial closure of the affricate. Select Fricative release to inspect the next teaching snapshot.")
    private val lateral = phase("alveolar-lateral", "Lateral tongue contact", "The tongue tip makes alveolar contact in a source lateral gesture. Surrounding vowels and syllable position can change English /l/.")
    private fun voicedPair(phase: ConsonantModelPhase) = ConsonantModelReference(listOf(phase),
        "The voiced and voiceless targets share this mouth shape. Throat vibration and airflow are not shown. This is an unreviewed English teaching reference in a fixed /a/ context, not the selected word's exact articulation.")
    private val affricate = ConsonantModelReference(listOf(affricateClosure, postalveolar.copy(label = "Fricative release")),
        "Inspect the closure and friction snapshots separately. They are not timed animation or synchronized with audio. /tʃ/ and /dʒ/ share these oral gestures; voicing is not shown. English suitability is unreviewed.")
    val byPhoneme: Map<String, ConsonantModelReference> = buildMap {
        for ((sounds, gesture) in listOf(
            listOf("p", "b") to bilabial, listOf("t", "d") to alveolar,
            listOf("k", "ɡ") to velar, listOf("f", "v") to labiodental,
            listOf("θ", "ð") to dental, listOf("s", "z") to sibilant,
            listOf("ʃ", "ʒ") to postalveolar
        )) sounds.forEach { put(it, voicedPair(gesture)) }
        put("tʃ", affricate); put("dʒ", affricate)
        put("l", ConsonantModelReference(listOf(lateral), "Original source lateral gesture in a fixed /a/ context; unreviewed as an English /l/ target."))
        put("w", ConsonantModelReference(listOf(phase("goose", "Rounded starting snapshot",
            "Rounded lips and a raised back tongue, reusing the /u/ source geometry as a starting illustration. The glide into a vowel is not shown.")),
            "Unreviewed illustrative reuse of JD3 u, not an upstream /w/ preset or a validated English /w/ trajectory."))
    }
    fun forPhoneme(phoneme: String): ConsonantModelReference? = byPhoneme[phoneme]
    fun pendingReason(phoneme: String): String? = if (phoneme == "r")
        "The supplied speaker has no matching English /r/ preset. A bunched or curled-tongue reference needs a separate reviewed design; placement guidance remains available." else null
}
