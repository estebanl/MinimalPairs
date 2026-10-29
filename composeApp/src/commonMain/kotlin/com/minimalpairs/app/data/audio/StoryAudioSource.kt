package com.minimalpairs.app.data.audio

enum class StoryAudioClip { PROMPT, PHRASE, INTENDED, TRAP }

fun interface StoryAudioSource {
    suspend fun load(storyId: String, clip: StoryAudioClip): ShortArray?
}

object BundledStoryAudioSource : StoryAudioSource {
    override suspend fun load(storyId: String, clip: StoryAudioClip): ShortArray? = when (clip) {
        StoryAudioClip.PROMPT -> ElevenLabsAudioRepository.loadMeaningTrapPromptPcm(storyId)
        StoryAudioClip.PHRASE -> ElevenLabsAudioRepository.loadMeaningTrapPhrasePcm(storyId)
        StoryAudioClip.INTENDED -> ElevenLabsAudioRepository.loadMeaningTrapOutcomePcm(storyId, true)
        StoryAudioClip.TRAP -> ElevenLabsAudioRepository.loadMeaningTrapOutcomePcm(storyId, false)
    }
}
