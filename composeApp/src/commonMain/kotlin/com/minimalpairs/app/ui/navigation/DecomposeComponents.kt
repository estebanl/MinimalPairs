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
                DefaultMeaningTrapComponent(componentContext, ::onBack)
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

            val baseSamples = FormantSynthesizer.synthesizeVowel(
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

// --- Meaning Trap & Prosody Blueprint Components ---
interface MeaningTrapComponent { fun onBack() }
class DefaultMeaningTrapComponent(componentContext: ComponentContext, private val onBackClicked: () -> Unit) :
    MeaningTrapComponent, ComponentContext by componentContext { override fun onBack() = onBackClicked() }

interface ProsodyComponent { fun onBack() }
class DefaultProsodyComponent(componentContext: ComponentContext, private val onBackClicked: () -> Unit) :
    ProsodyComponent, ComponentContext by componentContext { override fun onBack() = onBackClicked() }
