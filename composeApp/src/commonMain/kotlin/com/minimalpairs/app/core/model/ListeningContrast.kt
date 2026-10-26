package com.minimalpairs.app.core.model

/** Listening drills may contrast any sounds; only established vowel drills have acoustic guidance. */
data class ListeningContrast(
    val id: String,
    val phoneme1: String,
    val phoneme2: String,
    val description: String,
    val pairs: List<MinimalPairWord>,
    val soundTip: String,
    val vowelReference: PhoneticContrast? = null
)
