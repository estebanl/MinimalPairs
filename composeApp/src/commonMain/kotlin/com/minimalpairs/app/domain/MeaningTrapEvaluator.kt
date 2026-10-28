package com.minimalpairs.app.domain

import com.minimalpairs.app.core.model.ComicStory
import com.minimalpairs.app.core.model.MeaningTrapBranch
import com.minimalpairs.app.core.model.PhonemeScoringResponse
import com.minimalpairs.app.data.catalog.VowelContrastsCatalog
import com.minimalpairs.app.data.remote.PhonemeScorerApi
import com.minimalpairs.app.data.remote.PracticeScoringException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.abs

/** The phrase supplies alignment context; only the differing vowel chooses an ending. */
internal class MeaningTrapEvaluator(private val scorer: PhonemeScorerApi) {
    data class Evaluation(
        val branch: MeaningTrapBranch,
        val intendedScore: Float,
        val trapScore: Float,
        val intendedPhoneme: String,
        val trapPhoneme: String
    )

    suspend fun evaluate(samples: ShortArray, story: ComicStory): Evaluation {
        val intendedWord = story.intendedIpa.split(" ")
        val trapWord = story.trapIpa.split(" ")
        require(intendedWord.size == trapWord.size)
        val wordIndex = intendedWord.indices.filter { intendedWord[it] != trapWord[it] }.single()
        val intendedPhoneme = intendedWord[wordIndex]
        val trapPhoneme = trapWord[wordIndex]
        val contrast = requireNotNull(VowelContrastsCatalog.getContrastById(story.contrastId))
        require(setOf(intendedPhoneme, trapPhoneme) == setOf(contrast.phoneme1, contrast.phoneme2))
        val intendedTokens = story.targetPhraseIpa.split(" ")
        val index = story.targetPhonemeIndex
        require(intendedTokens.getOrNull(index) == intendedPhoneme)
        require(intendedTokens.drop(index - wordIndex).take(intendedWord.size) == intendedWord)
        val trapTokens = intendedTokens.toMutableList().apply { this[index] = trapPhoneme }
        val wordPattern = Regex("\\b${Regex.escape(story.intendedWord)}\\b")
        require(wordPattern.findAll(story.targetPhrase).count() == 1)
        val trapPhrase = wordPattern.replace(story.targetPhrase, story.trapWord)

        // A whole-clip acoustic estimate cannot isolate a vowel in a spoken sentence.
        // Never turn that fallback into a story ending or a personal practice result.
        val intended = scorer.scorePhraseAudio(samples, story.targetPhrase, story.targetPhraseIpa, index, trapPhoneme)
        currentCoroutineContext().ensureActive()
        validate(intended, intendedTokens, index, trapPhoneme, samples.size / 16000f)
        val alternate = scorer.scorePhraseAudio(samples, trapPhrase, trapTokens.joinToString(" "), index, intendedPhoneme)
        currentCoroutineContext().ensureActive()
        validate(alternate, trapTokens, index, intendedPhoneme, samples.size / 16000f)
        if (intended.modelId != alternate.modelId) {
            throw PracticeScoringException("The scoring model changed during this reply. Please record the phrase again. No result was saved.")
        }
        val intendedRegion = intended.phonemes[index]
        val alternateRegion = alternate.phonemes[index]
        if (abs(intendedRegion.startTime - alternateRegion.startTime) > .001f ||
            abs(intendedRegion.endTime - alternateRegion.endTime) > .001f ||
            abs(requireNotNull(intended.contrastEvidence) - requireNotNull(alternate.contrastEvidence)) > .001f
        ) {
            throw PracticeScoringException("The two sounds could not be compared in the same part of your reply. Please record the phrase again. No result was saved.")
        }
        val intendedScore = intended.phonemes[index].gopScore
        val trapScore = alternate.phonemes[index].gopScore
        // Conditional pair confidence needs at least 4:1 support for an ending.
        // This is a conservative interaction rule, not pronunciation accuracy.
        val branch = when {
            intendedScore >= 80f && intendedScore - trapScore >= 15f -> MeaningTrapBranch.INTENDED_SUCCESS
            trapScore >= 80f && trapScore - intendedScore >= 15f -> MeaningTrapBranch.TRAP_TRIGGERED
            else -> MeaningTrapBranch.PENDING
        }
        return Evaluation(branch, intendedScore, trapScore, intendedPhoneme, trapPhoneme)
    }

    private fun validate(result: PhonemeScoringResponse, tokens: List<String>, index: Int, alternative: String, duration: Float) {
        if (result.scoringMethod != "neural_ctc_phrase_contrast" || result.modelId.isNullOrBlank()) {
            throw PracticeScoringException("Phrase scoring is unavailable. Check the phoneme service connection and try again. No ending was chosen and no result was saved.")
        }
        if (!result.overallScore.isFinite() || result.overallScore !in 0f..100f ||
            result.alternativePhoneme != alternative || result.contrastEvidence?.let { it.isFinite() && it in .1f..1f } != true ||
            result.targetPhonemeIndex != index || result.phonemes.map { it.phoneme } != tokens ||
            result.phonemes.any { !it.gopScore.isFinite() || it.gopScore !in 0f..100f ||
                !it.startTime.isFinite() || !it.endTime.isFinite() || it.startTime < 0f ||
                it.endTime <= it.startTime || it.endTime > duration + .001f } ||
            result.phonemes.zipWithNext().any { (before, after) -> before.endTime > after.startTime } ||
            abs(result.overallScore - result.phonemes[index].gopScore) > .001f
        ) {
            throw PracticeScoringException("Couldn't align this reply reliably. Please record the full phrase again. No result was saved.")
        }
    }
}
