package com.minimalpairs.app.data.catalog

/** Broad British-English word transcriptions; /r/ is realized as model [ɹ]. */
object ConsonantScoringTargets {
    data class Target(val tokens: List<String>, val index: Int) {
        val ipa: String get() = tokens.joinToString(" ")
    }

    private val words = mapOf(
        "pear" to "p eə", "bear" to "b eə", "pack" to "p æ k", "back" to "b æ k",
        "pig" to "p ɪ ɡ", "big" to "b ɪ ɡ", "rope" to "r əʊ p", "robe" to "r əʊ b",
        "two" to "t uː", "do" to "d uː", "tear" to "t ɪə", "dear" to "d ɪə",
        "cart" to "k ɑː t", "card" to "k ɑː d", "wet" to "w e t", "wed" to "w e d",
        "coat" to "k əʊ t", "goat" to "ɡ əʊ t", "class" to "k l ɑː s", "glass" to "ɡ l ɑː s",
        "bag" to "b æ ɡ", "cold" to "k əʊ l d", "gold" to "ɡ əʊ l d",
        "fan" to "f æ n", "van" to "v æ n", "leaf" to "l iː f", "leave" to "l iː v",
        "few" to "f j uː", "view" to "v j uː", "ferry" to "f e r i", "very" to "v e r i",
        "think" to "θ ɪ ŋ k", "sink" to "s ɪ ŋ k", "thin" to "θ ɪ n", "this" to "ð ɪ s",
        "sue" to "s uː", "zoo" to "z uː", "price" to "p r aɪ s", "prize" to "p r aɪ z",
        "bus" to "b ʌ s", "buzz" to "b ʌ z", "sip" to "s ɪ p", "zip" to "z ɪ p",
        "slip" to "s l ɪ p", "ship" to "ʃ ɪ p", "shoe" to "ʃ uː", "measure" to "m e ʒ ə",
        "chair" to "tʃ eə", "jam" to "dʒ æ m", "cheap" to "tʃ iː p",
        "light" to "l aɪ t", "right" to "r aɪ t", "grass" to "ɡ r ɑː s",
        "fly" to "f l aɪ", "fry" to "f r aɪ", "belly" to "b e l i", "berry" to "b e r i",
        "vet" to "v e t", "wine" to "w aɪ n", "vine" to "v aɪ n", "west" to "w e s t",
        "vest" to "v e s t", "wheel" to "w iː l", "veal" to "v iː l"
    )

    fun find(contrastId: String, word: String, phoneme: String): Target? {
        val contrast = ListeningContrastsCatalog.getContrastById(contrastId) ?: return null
        if (contrast.vowelReference != null) return null
        val first = when (phoneme) {
            contrast.phoneme1 -> true
            contrast.phoneme2 -> false
            else -> return null
        }
        if (contrast.pairs.none { (if (first) it.word1 else it.word2).equals(word, true) }) return null
        val tokens = words[word.lowercase()]?.split(" ") ?: return null
        // Explicitly reject ambiguity rather than silently evaluating the wrong occurrence.
        if (tokens.count { it == phoneme } != 1) return null
        return Target(tokens, tokens.indexOf(phoneme))
    }
}
