package com.minimalpairs.app.data.audio

enum class StoryAudioClip { PROMPT, PHRASE, INTENDED, TRAP }

fun interface StoryAudioSource {
    suspend fun load(storyId: String, clip: StoryAudioClip): ShortArray?
}

object BundledStoryAudioSource : StoryAudioSource {
    override suspend fun load(storyId: String, clip: StoryAudioClip): ShortArray? = when (clip) {
        StoryAudioClip.PROMPT -> BundledAudioRepository.loadMeaningTrapPromptPcm(storyId)
        StoryAudioClip.PHRASE -> BundledAudioRepository.loadMeaningTrapPhrasePcm(storyId)
        StoryAudioClip.INTENDED -> BundledAudioRepository.loadMeaningTrapOutcomePcm(storyId, true)
        StoryAudioClip.TRAP -> BundledAudioRepository.loadMeaningTrapOutcomePcm(storyId, false)
    }
}
