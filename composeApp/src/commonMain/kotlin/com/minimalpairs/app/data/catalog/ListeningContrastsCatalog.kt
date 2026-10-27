package com.minimalpairs.app.data.catalog

import com.minimalpairs.app.core.model.ListeningContrast
import com.minimalpairs.app.core.model.MinimalPairWord

/** All Listen exercises, including the samples in ship_or_sheep_minimal_pairs.md. */
object ListeningContrastsCatalog {
    private fun pairs(vararg words: Pair<String, String>) = words.map { MinimalPairWord(it.first, it.second) }

    private val additionalVowelPairs = mapOf(
        "i_vs_I" to pairs("peel" to "pill", "bean" to "bin", "leave" to "live", "heel" to "hill"),
        "ae_vs_e" to pairs("bat" to "bet", "sand" to "send"),
        "u_vs_uu" to pairs("foot" to "boot"),
        "o_vs_or" to pairs("Don" to "Dawn")
    )

    private val vowelTips = mapOf(
        "i_vs_I" to "For “sheep,” lift your tongue toward the front and hold the sound a little longer. For “ship,” relax your tongue slightly lower and keep the sound short.",
        "ae_vs_e" to "Open your jaw a little more for “bad.” For “bed,” bring your jaw slightly closer and keep your tongue near the front.",
        "a_vs_uh" to "For “heart,” open your mouth and hold the vowel longer. The vowel in “hut” is shorter and more relaxed.",
        "u_vs_uu" to "Both sounds use rounded lips. For “fool,” round them more and hold the sound longer. For “full,” relax them and keep it short.",
        "o_vs_or" to "Keep your lips rounded. The vowel in “cot” is more open and shorter; the vowel in “caught” is usually held longer.",
        "ae_vs_uh" to "For “cat,” open your jaw and keep your tongue toward the front. For “cut,” relax your tongue toward the center.",
        "e_vs_I" to "For “pen,” let your jaw open a little more. For “pin,” lift your tongue slightly higher. Both sounds stay near the front."
    )

    val defaultContrasts: List<ListeningContrast> by lazy {
        VowelContrastsCatalog.defaultContrasts.map { vowel ->
            ListeningContrast(
                id = vowel.id,
                phoneme1 = vowel.phoneme1,
                phoneme2 = vowel.phoneme2,
                description = vowel.description,
                pairs = (vowel.pairs + additionalVowelPairs[vowel.id].orEmpty()).map { pair ->
                    if (pair.word2 == "luke") pair.copy(word2 = "Luke") else pair
                },
                soundTip = vowelTips.getValue(vowel.id),
                vowelReference = vowel
            )
        } + listOf(
            ListeningContrast(
                "p_vs_b", "p", "b", "Voiceless vs. voiced bilabial stop",
                pairs("pear" to "bear", "pack" to "back", "pig" to "big", "rope" to "robe"),
                "Close both lips, then release. For /p/, let out a small puff of air. For /b/, add vibration in your throat."
            ),
            ListeningContrast(
                "t_vs_d", "t", "d", "Voiceless vs. voiced alveolar stop",
                pairs("two" to "do", "tear" to "dear", "cart" to "card", "wet" to "wed"),
                "Touch your tongue just behind your upper teeth, then release. /t/ has no throat vibration; /d/ adds vibration. Here, “tear” means a drop from your eye and rhymes with “dear.”"
            ),
            ListeningContrast(
                "k_vs_g", "k", "ɡ", "Voiceless vs. voiced velar stop",
                pairs("coat" to "goat", "class" to "glass", "back" to "bag", "cold" to "gold"),
                "Lift the back of your tongue, then release. /k/ has no throat vibration; /ɡ/ adds vibration."
            ),
            ListeningContrast(
                "f_vs_v", "f", "v", "Voiceless vs. voiced labiodental fricative",
                pairs("fan" to "van", "leaf" to "leave", "few" to "view", "ferry" to "very"),
                "Rest your upper teeth lightly on your lower lip and let air flow. Keep /f/ unvoiced; add throat vibration for /v/."
            ),
            ListeningContrast(
                "th_vs_s", "θ", "s", "Dental vs. alveolar fricative",
                pairs("think" to "sink"),
                "For /θ/ in “think,” place your tongue lightly between your teeth. For /s/ in “sink,” keep it behind your teeth. Let air flow without throat vibration."
            ),
            ListeningContrast(
                "th_vs_dh", "θ", "ð", "Voiceless vs. voiced dental fricative",
                pairs("thin" to "this"),
                "Place your tongue lightly between your teeth. /θ/ in “thin” has no throat vibration; /ð/ in “this” adds vibration. These words also have different final sounds."
            ),
            ListeningContrast(
                "s_vs_z", "s", "z", "Voiceless vs. voiced alveolar fricative",
                pairs("sue" to "zoo", "price" to "prize", "bus" to "buzz", "sip" to "zip"),
                "Keep your tongue close behind your upper teeth and let air hiss. /s/ has no throat vibration; /z/ adds a buzz."
            ),
            ListeningContrast(
                "s_vs_sh", "s", "ʃ", "Alveolar vs. postalveolar fricative",
                pairs("slip" to "ship"),
                "For /s/, keep your tongue near your front teeth. For /ʃ/ in “ship,” move it slightly back and round your lips a little. “Slip” also includes /l/."
            ),
            ListeningContrast(
                "sh_vs_zh", "ʃ", "ʒ", "Voiceless vs. voiced postalveolar fricative",
                pairs("shoe" to "measure"),
                "Compare /ʃ/ at the start of “shoe” with /ʒ/ in the middle of “measure.” Use a similar tongue position, adding throat vibration for /ʒ/. This drill compares sounds within different words."
            ),
            ListeningContrast(
                "ch_vs_j", "tʃ", "dʒ", "Voiceless vs. voiced postalveolar affricate",
                pairs("chair" to "jam"),
                "Begin with a brief stop, then release air. /tʃ/ in “chair” is unvoiced; /dʒ/ in “jam” adds throat vibration. This drill compares the opening sounds of different words."
            ),
            ListeningContrast(
                "ch_vs_sh", "tʃ", "ʃ", "Affricate vs. fricative",
                pairs("cheap" to "ship"),
                "For /tʃ/ in “cheap,” briefly stop the air before releasing it. For /ʃ/ in “ship,” let air flow continuously. These words also have different vowels."
            ),
            ListeningContrast(
                "l_vs_r", "l", "r", "Lateral vs. rhotic approximant",
                pairs("light" to "right", "glass" to "grass", "fly" to "fry", "belly" to "berry"),
                "For /l/, touch your tongue just behind your upper teeth. For American /r/, draw your tongue back without touching the roof of your mouth."
            ),
            ListeningContrast(
                "w_vs_v", "w", "v", "Rounded glide vs. labiodental fricative",
                pairs("wet" to "vet", "wine" to "vine", "west" to "vest", "wheel" to "veal"),
                "For /w/, round your lips and glide into the vowel. For /v/, rest your upper teeth on your lower lip and let the air buzz."
            )
        )
    }

    fun getContrastById(id: String): ListeningContrast? = defaultContrasts.firstOrNull { it.id == id }
}
