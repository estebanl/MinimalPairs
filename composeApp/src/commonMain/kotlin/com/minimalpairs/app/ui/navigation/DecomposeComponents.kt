package com.minimalpairs.app.ui.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.bringToFront
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.minimalpairs.app.core.dsp.FormantSynthesizer
import com.minimalpairs.app.core.dsp.LpcFormantExtractor
import com.minimalpairs.app.core.dsp.MaskingNoiseMixer
import com.minimalpairs.app.core.dsp.TimeStretchProcessor
import com.minimalpairs.app.core.hal.AudioPlayer
import com.minimalpairs.app.core.hal.AudioRecorder
import com.minimalpairs.app.core.hal.AudioRecorderState
import com.minimalpairs.app.core.hal.createAudioPlayer
import com.minimalpairs.app.core.hal.createAudioRecorder
import com.minimalpairs.app.core.model.DrillType
import com.minimalpairs.app.core.model.MinimalPairWord
import com.minimalpairs.app.core.model.PhoneticContrast
import com.minimalpairs.app.core.model.PlaybackSpeed
import com.minimalpairs.app.data.audio.ElevenLabsAudioRepository
import com.minimalpairs.app.data.catalog.VowelContrastsCatalog
import com.minimalpairs.app.data.remote.PhonemeScorerApi
import com.minimalpairs.app.data.remote.PhonemeScorerClient
import com.minimalpairs.app.domain.srs.DrillProgressionManager
import com.minimalpairs.app.domain.srs.ModifiedSm2Engine
import com.minimalpairs.app.domain.srs.PhonemeConfusionTracker
import com.minimalpairs.app.domain.srs.Sm2State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.random.Random

// --- Navigation Stack Configurations ---
@Serializable
sealed interface RootConfig {
    @Serializable
    data object Dashboard : RootConfig

    @Serializable
    data class EarTrainer(val contrastId: String) : RootConfig

    @Serializable
    data class ArticulatorLab(val contrastId: String? = null) : RootConfig

    @Serializable
    data object ConfusionMatrix : RootConfig

    @Serializable
    data object MeaningTrap : RootConfig

    @Serializable
    data object Prosody : RootConfig
}

sealed interface RootChild {
    data class Dashboard(val component: DashboardComponent) : RootChild
    data class EarTrainer(val component: EarTrainerComponent) : RootChild
    data class ArticulatorLab(val component: ArticulatorLabComponent) : RootChild
    data class ConfusionMatrix(val component: ConfusionMatrixComponent) : RootChild
    data class MeaningTrap(val component: MeaningTrapComponent) : RootChild
    data class Prosody(val component: ProsodyComponent) : RootChild
}

interface RootComponent {
    val childStack: Value<ChildStack<*, RootChild>>
    fun onNavigateToDashboard()
    fun onNavigateToEarTrainer(contrastId: String)
    fun onNavigateToArticulatorLab(contrastId: String? = null)
    fun onNavigateToConfusionMatrix()
    fun onNavigateToMeaningTrap()
    fun onNavigateToProsody()
    fun onBack()
}

class DefaultRootComponent(
    componentContext: ComponentContext,
    private val confusionTracker: PhonemeConfusionTracker = PhonemeConfusionTracker(),
    private val scorerApi: PhonemeScorerApi = PhonemeScorerClient()
) : RootComponent, ComponentContext by componentContext {

    private val navigation = StackNavigation<RootConfig>()

    override val childStack: Value<ChildStack<*, RootChild>> =
        childStack(
            source = navigation,
            serializer = RootConfig.serializer(),
            initialConfiguration = RootConfig.Dashboard,
            handleBackButton = true,
            childFactory = ::createChild
        )

    private fun createChild(config: RootConfig, componentContext: ComponentContext): RootChild =
        when (config) {
            is RootConfig.Dashboard -> RootChild.Dashboard(
                DefaultDashboardComponent(componentContext, ::onNavigateToEarTrainer)
            )
            is RootConfig.EarTrainer -> RootChild.EarTrainer(
                DefaultEarTrainerComponent(
                    componentContext = componentContext,
                    contrastId = config.contrastId,
                    confusionTracker = confusionTracker,
                    onBackClicked = ::onBack
                )
            )
            is RootConfig.ArticulatorLab -> RootChild.ArticulatorLab(
                DefaultArticulatorLabComponent(
                    componentContext = componentContext,
                    contrastId = config.contrastId,
                    scorerApi = scorerApi,
                    onBackClicked = ::onBack
                )
            )
            is RootConfig.ConfusionMatrix -> RootChild.ConfusionMatrix(
                DefaultConfusionMatrixComponent(componentContext, confusionTracker, ::onBack)
            )
            is RootConfig.MeaningTrap -> RootChild.MeaningTrap(
                DefaultMeaningTrapComponent(
                    componentContext = componentContext,
                    confusionTracker = confusionTracker,
                    scorerApi = scorerApi,
                    onBackClicked = ::onBack
                )
            )
            is RootConfig.Prosody -> RootChild.Prosody(
                DefaultProsodyComponent(componentContext, ::onBack)
            )
        }

    override fun onNavigateToDashboard() = navigation.bringToFront(RootConfig.Dashboard)
    override fun onNavigateToEarTrainer(contrastId: String) = navigation.bringToFront(RootConfig.EarTrainer(contrastId))
    override fun onNavigateToArticulatorLab(contrastId: String?) = navigation.bringToFront(RootConfig.ArticulatorLab(contrastId))
    override fun onNavigateToConfusionMatrix() = navigation.bringToFront(RootConfig.ConfusionMatrix)
    override fun onNavigateToMeaningTrap() = navigation.bringToFront(RootConfig.MeaningTrap)
    override fun onNavigateToProsody() = navigation.bringToFront(RootConfig.Prosody)
    override fun onBack() = navigation.pop()
}

// --- Dashboard Component ---
interface DashboardComponent {
    val contrasts: List<PhoneticContrast>
    fun onSelectContrast(contrastId: String)
}

class DefaultDashboardComponent(
    componentContext: ComponentContext,
    private val onSelect: (String) -> Unit
) : DashboardComponent, ComponentContext by componentContext {
    override val contrasts: List<PhoneticContrast> = VowelContrastsCatalog.defaultContrasts
    override fun onSelectContrast(contrastId: String) = onSelect(contrastId)
}

// --- Ear Trainer Component ---
data class EarTrainerUiState(
    val contrast: PhoneticContrast,
    val currentPair: MinimalPairWord,
    val targetWord: String,
    val targetPhoneme: String,
    val drillType: DrillType = DrillType.FORCED_CHOICE,
    val playbackSpeed: PlaybackSpeed = PlaybackSpeed.NORMAL,
    val consecutiveFailures: Int = 0,
    val showFormantHint: Boolean = false,
    val isPlayingAudio: Boolean = false,
    val lastResultCorrect: Boolean? = null,
    val feedbackMessage: String = "",
    val sm2State: Sm2State = Sm2State()
)

interface EarTrainerComponent {
    val state: StateFlow<EarTrainerUiState>
    fun playPrompt()
    fun onSelectAnswer(chosenWord: String, latencySeconds: Float)
    fun setPlaybackSpeed(speed: PlaybackSpeed)
    fun dismissFormantHint()
    fun nextTrial()
    fun onBack()
}

class DefaultEarTrainerComponent(
    componentContext: ComponentContext,
    contrastId: String,
    private val confusionTracker: PhonemeConfusionTracker,
    private val onBackClicked: () -> Unit,
    private val audioPlayer: AudioPlayer = createAudioPlayer()
) : EarTrainerComponent, ComponentContext by componentContext {

    private val contrast = VowelContrastsCatalog.getContrastById(contrastId)
        ?: VowelContrastsCatalog.defaultContrasts.first()

    private val progression = DrillProgressionManager()
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _state = MutableStateFlow(createInitialState())
    override val state: StateFlow<EarTrainerUiState> = _state.asStateFlow()

    private fun createInitialState(): EarTrainerUiState {
        val initialPair = contrast.pairs.random()
        val isWord1 = Random.nextBoolean()
        val targetWord = if (isWord1) initialPair.word1 else initialPair.word2
        val targetPhoneme = if (isWord1) contrast.phoneme1 else contrast.phoneme2
        return EarTrainerUiState(
            contrast = contrast,
            currentPair = initialPair,
            targetWord = targetWord,
            targetPhoneme = targetPhoneme
        )
    }

    override fun playPrompt() {
        scope.launch {
            _state.value = _state.value.copy(isPlayingAudio = true)
            val currentState = _state.value
            val isWord1 = currentState.targetWord == currentState.currentPair.word1
            val bounds = if (isWord1) contrast.boundsPhoneme1 else contrast.boundsPhoneme2

            val baseSamples = ElevenLabsAudioRepository.loadWordPcm(currentState.targetWord)
                ?: FormantSynthesizer.synthesizeVowel(
                    f1 = (bounds.f1MinHz + bounds.f1MaxHz) / 2f,
                    f2 = (bounds.f2MinHz + bounds.f2MaxHz) / 2f,
                    durationMs = bounds.minDurationMs ?: 150
                )

            // Apply 0.75x phase-vocoded / WSOLA pitch-preserving stretch if slow mode active
            val stretched = if (currentState.playbackSpeed == PlaybackSpeed.SLOW_DIAGNOSTIC) {
                TimeStretchProcessor.process(baseSamples, 0.75f)
            } else {
                baseSamples
            }

            // Apply Masking Noise if in MASKING_NOISE drill mode
            val finalSamples = if (currentState.drillType == DrillType.MASKING_NOISE) {
                MaskingNoiseMixer.mix(stretched, snrDb = 8.0f)
            } else {
                stretched
            }

            audioPlayer.play(finalSamples) {
                _state.value = _state.value.copy(isPlayingAudio = false)
            }
        }
    }

    override fun onSelectAnswer(chosenWord: String, latencySeconds: Float) {
        val current = _state.value
        val isCorrect = chosenWord.equals(current.targetWord, ignoreCase = true)
        val chosenPhoneme = if (chosenWord == current.currentPair.word1) contrast.phoneme1 else contrast.phoneme2

        // Record trial in confusion tracker
        confusionTracker.recordTrial(current.targetPhoneme, chosenPhoneme, isCorrect)

        // Evaluate SM-2 quality and state
        val q = ModifiedSm2Engine.calculateQuality(isCorrect, latencySeconds, progression.consecutiveFailures)
        val nextSm2 = ModifiedSm2Engine.review(current.sm2State, q)

        // Progress drills and diagnostic flags
        val feedback = progression.onTrialCompleted(isCorrect)
        val nextDrill = progression.resolveDrillType(nextSm2.masteryScore)

        _state.value = current.copy(
            lastResultCorrect = isCorrect,
            feedbackMessage = if (isCorrect) "Excellent! Correctly identified: /${current.targetPhoneme}/"
            else "Notice the difference: Target was /${current.targetPhoneme}/",
            playbackSpeed = feedback.speed,
            consecutiveFailures = feedback.consecutiveFailures,
            showFormantHint = feedback.showFormantHint,
            sm2State = nextSm2,
            drillType = nextDrill
        )
    }

    override fun setPlaybackSpeed(speed: PlaybackSpeed) {
        progression.setSpeedManual(speed)
        _state.value = _state.value.copy(playbackSpeed = speed)
    }

    override fun dismissFormantHint() {
        progression.dismissFormantHint()
        _state.value = _state.value.copy(showFormantHint = false)
    }

    override fun nextTrial() {
        val nextPair = contrast.pairs.random()
        val isWord1 = Random.nextBoolean()
        val targetWord = if (isWord1) nextPair.word1 else nextPair.word2
        val targetPhoneme = if (isWord1) contrast.phoneme1 else contrast.phoneme2

        _state.value = _state.value.copy(
            currentPair = nextPair,
            targetWord = targetWord,
            targetPhoneme = targetPhoneme,
            lastResultCorrect = null,
            feedbackMessage = ""
        )
        playPrompt()
    }

    override fun onBack() = onBackClicked()
}

// --- Articulator Lab Component ---
data class ArticulatorLabUiState(
    val contrast: PhoneticContrast,
    val recorderState: AudioRecorderState = AudioRecorderState.UNINITIALIZED,
    val liveF1: Float = 0f,
    val liveF2: Float = 0f,
    val liveF3: Float = 0f,
    val liveDurationMs: Int = 0,
    val overallGopScore: Float? = null,
    val isTargetPhoneme1: Boolean = true
)

interface ArticulatorLabComponent {
    val state: StateFlow<ArticulatorLabUiState>
    fun toggleRecording()
    fun testSynthesizedVowel(isPhoneme1: Boolean)
    fun switchTargetPhoneme(isPhoneme1: Boolean)
    fun onBack()
}

class DefaultArticulatorLabComponent(
    componentContext: ComponentContext,
    contrastId: String?,
    private val scorerApi: PhonemeScorerApi,
    private val onBackClicked: () -> Unit,
    private val recorder: AudioRecorder = createAudioRecorder()
) : ArticulatorLabComponent, ComponentContext by componentContext {

    private val contrast = contrastId?.let { VowelContrastsCatalog.getContrastById(it) }
        ?: VowelContrastsCatalog.defaultContrasts.first()

    private val scope = CoroutineScope(Dispatchers.Default)
    private val _state = MutableStateFlow(ArticulatorLabUiState(contrast = contrast))
    override val state: StateFlow<ArticulatorLabUiState> = _state.asStateFlow()

    private val accumulatedSamples = mutableListOf<Short>()

    init {
        scope.launch {
            recorder.prepare()
            recorder.state.collect { recState ->
                _state.value = _state.value.copy(recorderState = recState)
            }
        }
    }

    override fun toggleRecording() {
        scope.launch {
            if (recorder.state.value == AudioRecorderState.RECORDING) {
                recorder.stop()
                val samples = accumulatedSamples.toShortArray()
                if (samples.isNotEmpty()) {
                    val targetPhoneme = if (_state.value.isTargetPhoneme1) contrast.phoneme1 else contrast.phoneme2
                    val scoring = scorerApi.scoreAudio(samples, "vowel", targetPhoneme, contrast.id)
                    _state.value = _state.value.copy(overallGopScore = scoring.overallScore)
                }
            } else {
                accumulatedSamples.clear()
                recorder.start { buffer ->
                    accumulatedSamples.addAll(buffer.toList())
                    if (buffer.isNotEmpty()) {
                        val analysis = LpcFormantExtractor.extract(buffer)
                        if (analysis.f1Hz > 150f) {
                            _state.value = _state.value.copy(
                                liveF1 = analysis.f1Hz,
                                liveF2 = analysis.f2Hz,
                                liveF3 = analysis.f3Hz,
                                liveDurationMs = analysis.durationMs
                            )
                        }
                    }
                }
            }
        }
    }

    override fun testSynthesizedVowel(isPhoneme1: Boolean) {
        scope.launch {
            val bounds = if (isPhoneme1) contrast.boundsPhoneme1 else contrast.boundsPhoneme2
            val midF1 = (bounds.f1MinHz + bounds.f1MaxHz) / 2f
            val midF2 = (bounds.f2MinHz + bounds.f2MaxHz) / 2f
            val testSamples = FormantSynthesizer.synthesizeVowel(
                f1 = midF1,
                f2 = midF2,
                durationMs = bounds.minDurationMs ?: 140
            )

            val analysis = LpcFormantExtractor.extract(testSamples)
            val scoring = scorerApi.scoreAudio(
                testSamples,
                if (isPhoneme1) contrast.pairs.first().word1 else contrast.pairs.first().word2,
                if (isPhoneme1) contrast.phoneme1 else contrast.phoneme2,
                contrast.id
            )

            _state.value = _state.value.copy(
                liveF1 = analysis.f1Hz,
                liveF2 = analysis.f2Hz,
                liveF3 = analysis.f3Hz,
                liveDurationMs = analysis.durationMs,
                overallGopScore = scoring.overallScore,
                isTargetPhoneme1 = isPhoneme1
            )
        }
    }

    override fun switchTargetPhoneme(isPhoneme1: Boolean) {
        _state.value = _state.value.copy(isTargetPhoneme1 = isPhoneme1)
    }

    override fun onBack() = onBackClicked()
}

// --- Confusion Matrix Component ---
interface ConfusionMatrixComponent {
    val confusionMap: Map<String, Map<String, Int>>
    val confidenceWeights: Map<String, Float>
    fun onBack()
}

class DefaultConfusionMatrixComponent(
    componentContext: ComponentContext,
    private val tracker: PhonemeConfusionTracker,
    private val onBackClicked: () -> Unit
) : ConfusionMatrixComponent, ComponentContext by componentContext {
    override val confusionMap: Map<String, Map<String, Int>> get() = tracker.getAllConfusions()
    override val confidenceWeights: Map<String, Float> get() = tracker.getAllWeights()
    override fun onBack() = onBackClicked()
}

// --- Module 3: Meaning Trap Component ---
data class MeaningTrapUiState(
    val activeStory: com.minimalpairs.app.core.model.ComicStory,
    val branch: com.minimalpairs.app.core.model.MeaningTrapBranch = com.minimalpairs.app.core.model.MeaningTrapBranch.PENDING,
    val isRecording: Boolean = false,
    val isPlayingAudio: Boolean = false,
    val gopScore: Float? = null,
    val feedbackText: String = ""
)

interface MeaningTrapComponent {
    val state: StateFlow<MeaningTrapUiState>
    val availableStories: List<com.minimalpairs.app.core.model.ComicStory>
    fun selectStory(storyId: String)
    fun playPromptAudio()
    fun playTargetPhraseAudio()
    fun playOutcomeAudio()
    fun toggleRecording()
    fun triggerSimulatedOutcome(isCorrect: Boolean)
    fun resetCurrentStory()
    fun onBack()
}

class DefaultMeaningTrapComponent(
    componentContext: ComponentContext,
    private val confusionTracker: PhonemeConfusionTracker,
    private val scorerApi: PhonemeScorerApi,
    private val onBackClicked: () -> Unit,
    private val recorder: AudioRecorder = createAudioRecorder(),
    private val audioPlayer: AudioPlayer = createAudioPlayer()
) : MeaningTrapComponent, ComponentContext by componentContext {

    override val availableStories: List<com.minimalpairs.app.core.model.ComicStory> =
        com.minimalpairs.app.data.catalog.MeaningTrapCatalog.stories

    private val scope = CoroutineScope(Dispatchers.Default)
    private val accumulatedSamples = mutableListOf<Short>()

    private val _state = MutableStateFlow(
        MeaningTrapUiState(activeStory = availableStories.first())
    )
    override val state: StateFlow<MeaningTrapUiState> = _state.asStateFlow()

    init {
        scope.launch {
            recorder.prepare()
        }
    }

    override fun selectStory(storyId: String) {
        val story = availableStories.firstOrNull { it.id == storyId } ?: return
        _state.value = MeaningTrapUiState(activeStory = story)
        playPromptAudio()
    }

    override fun playPromptAudio() {
        scope.launch {
            val storyId = _state.value.activeStory.id
            val pcm = ElevenLabsAudioRepository.loadMeaningTrapPromptPcm(storyId) ?: return@launch
            _state.value = _state.value.copy(isPlayingAudio = true)
            audioPlayer.play(pcm) {
                _state.value = _state.value.copy(isPlayingAudio = false)
            }
        }
    }

    override fun playTargetPhraseAudio() {
        scope.launch {
            val storyId = _state.value.activeStory.id
            val pcm = ElevenLabsAudioRepository.loadMeaningTrapPhrasePcm(storyId) ?: return@launch
            _state.value = _state.value.copy(isPlayingAudio = true)
            audioPlayer.play(pcm) {
                _state.value = _state.value.copy(isPlayingAudio = false)
            }
        }
    }

    override fun playOutcomeAudio() {
        val branch = _state.value.branch
        if (branch == com.minimalpairs.app.core.model.MeaningTrapBranch.PENDING) return
        val isSuccess = branch == com.minimalpairs.app.core.model.MeaningTrapBranch.INTENDED_SUCCESS
        scope.launch {
            val storyId = _state.value.activeStory.id
            val pcm = ElevenLabsAudioRepository.loadMeaningTrapOutcomePcm(storyId, isSuccess) ?: return@launch
            _state.value = _state.value.copy(isPlayingAudio = true)
            audioPlayer.play(pcm) {
                _state.value = _state.value.copy(isPlayingAudio = false)
            }
        }
    }

    override fun toggleRecording() {
        scope.launch {
            if (_state.value.isRecording) {
                recorder.stop()
                _state.value = _state.value.copy(isRecording = false)
                val samples = accumulatedSamples.toShortArray()
                if (samples.isNotEmpty()) {
                    val currentStory = _state.value.activeStory
                    val result = scorerApi.scoreAudio(
                        samples,
                        currentStory.intendedWord,
                        currentStory.intendedIpa,
                        currentStory.contrastId
                    )
                    val isSuccess = result.overallScore >= 70f
                    applyEvaluationResult(isSuccess, result.overallScore)
                }
            } else {
                accumulatedSamples.clear()
                _state.value = _state.value.copy(
                    isRecording = true,
                    branch = com.minimalpairs.app.core.model.MeaningTrapBranch.PENDING,
                    feedbackText = "Listening... Speak the phrase clearly!"
                )
                recorder.start { buffer ->
                    accumulatedSamples.addAll(buffer.toList())
                }
            }
        }
    }

    override fun triggerSimulatedOutcome(isCorrect: Boolean) {
        applyEvaluationResult(isCorrect, if (isCorrect) 92.5f else 48.0f)
    }

    private fun applyEvaluationResult(isCorrect: Boolean, score: Float) {
        val story = _state.value.activeStory
        val targetPhoneme = story.intendedIpa.replace("/", "").trim().split(" ").firstOrNull { it.isNotEmpty() } ?: story.intendedIpa
        val trapPhoneme = story.trapIpa.replace("/", "").trim().split(" ").firstOrNull { it.isNotEmpty() } ?: story.trapIpa

        if (isCorrect) {
            confusionTracker.recordTrial(targetPhoneme, targetPhoneme, true)
            _state.value = _state.value.copy(
                branch = com.minimalpairs.app.core.model.MeaningTrapBranch.INTENDED_SUCCESS,
                gopScore = score,
                feedbackText = "Splendid pronunciation! (${score.toInt()}% match for /${targetPhoneme}/)"
            )
        } else {
            confusionTracker.recordTrial(targetPhoneme, trapPhoneme, false)
            _state.value = _state.value.copy(
                branch = com.minimalpairs.app.core.model.MeaningTrapBranch.TRAP_TRIGGERED,
                gopScore = score,
                feedbackText = "Oh no, you slipped into the Meaning Trap! (${score.toInt()}% match)"
            )
        }
        playOutcomeAudio()
    }

    override fun resetCurrentStory() {
        _state.value = _state.value.copy(
            branch = com.minimalpairs.app.core.model.MeaningTrapBranch.PENDING,
            gopScore = null,
            feedbackText = ""
        )
    }

    override fun onBack() = onBackClicked()
}

// --- Module 4: Prosody Component ---
data class ProsodyUiState(
    val activePattern: com.minimalpairs.app.core.model.ProsodyPattern,
    val isRecording: Boolean = false,
    val isPlayingReference: Boolean = false,
    val evaluation: com.minimalpairs.app.core.dsp.ProsodyEvaluation? = null
)

interface ProsodyComponent {
    val state: StateFlow<ProsodyUiState>
    val availablePatterns: List<com.minimalpairs.app.core.model.ProsodyPattern>
    fun selectPattern(patternId: String)
    fun playReferenceAudio()
    fun toggleRecording()
    fun triggerSimulatedProsody(isGoodMatch: Boolean)
    fun resetEvaluation()
    fun onBack()
}

class DefaultProsodyComponent(
    componentContext: ComponentContext,
    private val onBackClicked: () -> Unit,
    private val recorder: AudioRecorder = createAudioRecorder(),
    private val audioPlayer: AudioPlayer = createAudioPlayer()
) : ProsodyComponent, ComponentContext by componentContext {

    override val availablePatterns: List<com.minimalpairs.app.core.model.ProsodyPattern> =
        com.minimalpairs.app.data.catalog.ProsodyCatalog.patterns

    private val scope = CoroutineScope(Dispatchers.Default)
    private val accumulatedSamples = mutableListOf<Short>()

    private val _state = MutableStateFlow(
        ProsodyUiState(activePattern = availablePatterns.first())
    )
    override val state: StateFlow<ProsodyUiState> = _state.asStateFlow()

    init {
        scope.launch {
            recorder.prepare()
        }
    }

    override fun selectPattern(patternId: String) {
        val pattern = availablePatterns.firstOrNull { it.id == patternId } ?: return
        _state.value = ProsodyUiState(activePattern = pattern)
    }

    override fun playReferenceAudio() {
        scope.launch {
            _state.value = _state.value.copy(isPlayingReference = true)
            val pattern = _state.value.activePattern
            val elevenLabsSpeech = ElevenLabsAudioRepository.loadProsodyPcm(pattern.id)
            val speechSamples = if (elevenLabsSpeech != null && elevenLabsSpeech.isNotEmpty()) {
                elevenLabsSpeech
            } else {
                val totalSamples = 16000 * 2 // 2 seconds fallback
                val syntheticSpeech = ShortArray(totalSamples)
                for (i in 0 until totalSamples) {
                    val normT = i.toFloat() / totalSamples
                    val f0 = interpolateTargetF0(normT, pattern.referenceContour)
                    val phase = 2.0 * kotlin.math.PI * f0 * (i.toDouble() / 16000.0)
                    val s = (kotlin.math.sin(phase) * 12000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    syntheticSpeech[i] = s
                }
                syntheticSpeech
            }

            audioPlayer.play(speechSamples) {
                _state.value = _state.value.copy(isPlayingReference = false)
            }
        }
    }

    private fun interpolateTargetF0(normT: Float, contour: List<Pair<Float, Float>>): Float {
        if (contour.isEmpty()) return 180f
        if (normT <= contour.first().first) return contour.first().second
        if (normT >= contour.last().first) return contour.last().second
        for (i in 0 until contour.size - 1) {
            val p1 = contour[i]
            val p2 = contour[i + 1]
            if (normT in p1.first..p2.first) {
                val span = (p2.first - p1.first).coerceAtLeast(1e-4f)
                val alpha = (normT - p1.first) / span
                return p1.second + alpha * (p2.second - p1.second)
            }
        }
        return contour.first().second
    }

    override fun toggleRecording() {
        scope.launch {
            if (_state.value.isRecording) {
                recorder.stop()
                _state.value = _state.value.copy(isRecording = false)
                val samples = accumulatedSamples.toShortArray()
                if (samples.isNotEmpty()) {
                    val frames = com.minimalpairs.app.core.dsp.PitchTracker.trackPitch(samples)
                    val eval = com.minimalpairs.app.core.dsp.PitchTracker.evaluateProsody(
                        frames,
                        _state.value.activePattern.referenceContour
                    )
                    _state.value = _state.value.copy(evaluation = eval)
                }
            } else {
                accumulatedSamples.clear()
                _state.value = _state.value.copy(isRecording = true, evaluation = null)
                recorder.start { buffer ->
                    accumulatedSamples.addAll(buffer.toList())
                }
            }
        }
    }

    override fun triggerSimulatedProsody(isGoodMatch: Boolean) {
        val pattern = _state.value.activePattern
        val sampleFrames = mutableListOf<com.minimalpairs.app.core.dsp.PitchFrame>()
        val totalSteps = 100
        for (i in 0..totalSteps) {
            val t = i / 100f
            val targetF0 = interpolateTargetF0(t, pattern.referenceContour)
            val userF0 = if (isGoodMatch) targetF0 + (kotlin.random.Random.nextFloat() * 10f - 5f)
            else 160f + (kotlin.random.Random.nextFloat() * 10f)
            sampleFrames.add(com.minimalpairs.app.core.dsp.PitchFrame(timeSeconds = t * 1.8f, f0Hz = userF0, isVoiced = true))
        }

        val eval = com.minimalpairs.app.core.dsp.PitchTracker.evaluateProsody(
            sampleFrames,
            pattern.referenceContour
        )
        _state.value = _state.value.copy(evaluation = eval)
    }

    override fun resetEvaluation() {
        _state.value = _state.value.copy(evaluation = null)
    }

    override fun onBack() = onBackClicked()
}
