package com.minimalpairs.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ComicOutcome(
    val illustrationEmoji: String,
    val soundEffect: String,
    val dialogue: String,
    val outcomeDescription: String
)

@Serializable
data class ComicStory(
    val id: String,
    val contrastId: String,
    val title: String,
    val premise: String,
    val intendedWord: String,
    val intendedIpa: String,
    val trapWord: String,
    val trapIpa: String,
    val promptCharacter: String,
    val promptDialogue: String,
    val promptInstruction: String,
    val targetPhrase: String,
    val targetPhraseIpa: String,
    val targetPhonemeIndex: Int,
    val intendedOutcome: ComicOutcome,
    val trapOutcome: ComicOutcome,
    val semanticTakeaway: String
)

enum class MeaningTrapBranch {
    PENDING,
    INTENDED_SUCCESS,
    TRAP_TRIGGERED
}
