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
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.essenty.lifecycle.doOnStop
import com.minimalpairs.app.core.dsp.FormantSynthesizer
import com.minimalpairs.app.core.dsp.LpcFormantExtractor
import com.minimalpairs.app.core.dsp.TimeStretchProcessor
import com.minimalpairs.app.core.hal.AudioPlayer
import com.minimalpairs.app.core.hal.AudioRecorder
import com.minimalpairs.app.core.hal.AudioRecorderState
import com.minimalpairs.app.core.hal.createAudioPlayer
import com.minimalpairs.app.core.hal.createAudioRecorder
import com.minimalpairs.app.core.model.DrillType
import com.minimalpairs.app.core.model.ListeningContrast
import com.minimalpairs.app.core.model.MinimalPairWord
import com.minimalpairs.app.core.model.PhoneticContrast
import com.minimalpairs.app.core.model.PlaybackSpeed
import com.minimalpairs.app.data.audio.ElevenLabsAudioRepository
import com.minimalpairs.app.data.catalog.ListeningContrastsCatalog
import com.minimalpairs.app.data.catalog.VowelContrastsCatalog
import com.minimalpairs.app.data.remote.PhonemeScorerApi
import com.minimalpairs.app.data.remote.PhonemeScorerClient
import com.minimalpairs.app.domain.srs.DrillProgressionManager
import com.minimalpairs.app.domain.srs.ModifiedSm2Engine
import com.minimalpairs.app.domain.srs.PhonemeConfusionTracker
import com.minimalpairs.app.domain.srs.Sm2State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.random.Random
import kotlin.time.Clock
import com.minimalpairs.app.data.local.*
import com.minimalpairs.app.data.progress.*
import kotlinx.coroutines.flow.collect
import kotlin.time.TimeMark
import kotlin.time.TimeSource

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

    private val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val progressDatabase = createDatabase(getDatabaseBuilder())
    private val progressRepository = ProgressRepository(progressDatabase.progressDao(), progressScope)
    init {
        lifecycle.doOnDestroy {
            val job = progressScope.coroutineContext[Job]!!
            job.cancel()
            CoroutineScope(Dispatchers.Default).launch {
                job.join()
                progressDatabase.close()
            }
        }
    }

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
                    onBackClicked = ::onBack,
                    progressRepository = progressRepository
                )
            )
            is RootConfig.ArticulatorLab -> RootChild.ArticulatorLab(
                DefaultArticulatorLabComponent(
                    componentContext = componentContext,
                    contrastId = config.contrastId,
                    scorerApi = scorerApi,
                    onBackClicked = ::onBack,
                    progressRepository = progressRepository
                )
            )
            is RootConfig.ConfusionMatrix -> RootChild.ConfusionMatrix(
                DefaultConfusionMatrixComponent(componentContext, progressRepository, ::onNavigateToEarTrainer, ::onBack, ::onNavigateToArticulatorLab)
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
    val contrasts: List<ListeningContrast>
    fun onSelectContrast(contrastId: String)
}

class DefaultDashboardComponent(
    componentContext: ComponentContext,
    private val onSelect: (String) -> Unit
) : DashboardComponent, ComponentContext by componentContext {
    override val contrasts: List<ListeningContrast> = ListeningContrastsCatalog.defaultContrasts
    override fun onSelectContrast(contrastId: String) = onSelect(contrastId)
}

// --- Ear Trainer Component ---
data class EarTrainerUiState(
    val contrast: ListeningContrast,
    val currentPair: MinimalPairWord,
    val targetWord: String,
    val targetPhoneme: String,
    val drillType: DrillType = DrillType.FORCED_CHOICE,
    val playbackSpeed: PlaybackSpeed = PlaybackSpeed.NORMAL,
    val consecutiveFailures: Int = 0,
    val showFormantHint: Boolean = false,
    val isPlayingAudio: Boolean = false,
    val playingOptionWord: String? = null,
    val hasPlayedPrompt: Boolean = false,
    val selectedWord: String? = null,
    val playbackError: String? = null,
    val lastResultCorrect: Boolean? = null,
    val feedbackMessage: String = "",
    val progressSaveError: String? = null,
    val isSavingProgress: Boolean = false,
    val sm2State: Sm2State = Sm2State()
)

interface EarTrainerComponent {
    val state: StateFlow<EarTrainerUiState>
    fun playPrompt()
    fun playOption(word: String)
    fun onSelectAnswer(chosenWord: String)
    fun setPlaybackSpeed(speed: PlaybackSpeed)
    fun dismissFormantHint()
    fun retryProgressSaves()
    fun nextTrial()
    fun onBack()
}

class DefaultEarTrainerComponent(
    componentContext: ComponentContext,
    contrastId: String,
    private val confusionTracker: PhonemeConfusionTracker,
    private val onBackClicked: () -> Unit,
    private val audioPlayer: AudioPlayer = createAudioPlayer(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val audioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val random: Random = Random.Default,
    private val loadWordPcm: suspend (String) -> ShortArray? = ElevenLabsAudioRepository::loadWordPcm,
    private val progressRepository: ProgressRepository? = null
) : EarTrainerComponent, ComponentContext by componentContext {

    private val contrast = ListeningContrastsCatalog.getContrastById(contrastId)
        ?: ListeningContrastsCatalog.defaultContrasts.first()

    private val progression = DrillProgressionManager()
    // UI actions and playback completions are serialized on the main dispatcher.
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var playbackJob: Job? = null
    private var stopJob: Job? = null
    private var playbackGeneration = 0L
    private var answerStartedAt: TimeMark? = null
    private var closed = false

    private val _state = MutableStateFlow(createInitialState())
    override val state: StateFlow<EarTrainerUiState> = _state.asStateFlow()

    init {
        progressRepository?.let { repository ->
            scope.launch {
                repository.state.collect { progress ->
                    _state.value = _state.value.copy(
                        progressSaveError = if (progress.canRetrySave) progress.error else null,
                        isSavingProgress = progress.isSaving
                    )
                    progress.contrasts.firstOrNull { it.contrastId == contrast.id }?.card?.let { card ->
                        val saved = Sm2State(card.repetitions, card.intervalDays, card.easeFactor, card.masteryScore)
                        _state.value = _state.value.copy(sm2State = saved, drillType = progression.resolveDrillType(saved.masteryScore))
                    }
                }
            }
        }
        lifecycle.doOnStop { stopPlayback() }
        lifecycle.doOnDestroy {
            closed = true
            stopPlayback()
            scope.cancel()
        }
    }

    private fun createInitialState(): EarTrainerUiState {
        val initialPair = contrast.pairs.random(random)
        val isWord1 = random.nextBoolean()
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
        playWord(_state.value.targetWord, isComparison = false)
    }

    override fun playOption(word: String) {
        val current = _state.value
        if (current.lastResultCorrect == null) return
        val option = listOf(current.currentPair.word1, current.currentPair.word2)
            .firstOrNull { it.equals(word, ignoreCase = true) } ?: return
        playWord(option, isComparison = true)
    }

    private fun playWord(word: String, isComparison: Boolean) {
        if (closed || _state.value.isPlayingAudio) return
        val currentState = _state.value
        val generation = ++playbackGeneration
        _state.value = currentState.copy(
            isPlayingAudio = true,
            playingOptionWord = word.takeIf { isComparison },
            playbackError = null
        )
        playbackJob = scope.launch {
            try {
                stopJob?.join()
                val finalSamples = withContext(audioDispatcher) {
                    val baseSamples = loadWordPcm(word)
                    check(baseSamples != null && baseSamples.isNotEmpty()) { "Whole-word audio is unavailable" }
                    if (currentState.playbackSpeed == PlaybackSpeed.SLOW_DIAGNOSTIC) {
                        TimeStretchProcessor.process(baseSamples, 0.75f)
                    } else {
                        baseSamples
                    }
                }

                ensureActive()
                audioPlayer.play(finalSamples) {
                    scope.launch completion@{
                        if (closed || generation != playbackGeneration || !_state.value.isPlayingAudio) return@completion
                        if (!isComparison && !_state.value.hasPlayedPrompt) answerStartedAt = timeSource.markNow()
                        _state.value = _state.value.copy(
                            isPlayingAudio = false,
                            playingOptionWord = null,
                            hasPlayedPrompt = _state.value.hasPlayedPrompt || !isComparison
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (!closed && generation == playbackGeneration) {
                    _state.value = _state.value.copy(
                        isPlayingAudio = false,
                        playingOptionWord = null,
                        playbackError = if (isComparison) "Couldn't play “$word”. Try again."
                        else "Couldn't play the word. Try again."
                    )
                }
            }
        }
    }

    override fun onSelectAnswer(chosenWord: String) {
        val current = _state.value
        if (closed || !current.hasPlayedPrompt || current.isPlayingAudio || current.lastResultCorrect != null) return
        val selectedWord = listOf(current.currentPair.word1, current.currentPair.word2)
            .firstOrNull { it.equals(chosenWord, ignoreCase = true) } ?: return
        val latencySeconds = ((answerStartedAt ?: return).elapsedNow().inWholeMilliseconds / 1000f).coerceAtLeast(.1f)
        val isCorrect = selectedWord == current.targetWord
        val chosenPhoneme = if (selectedWord == current.currentPair.word1) contrast.phoneme1 else contrast.phoneme2

        // Record trial in confusion tracker
        confusionTracker.recordTrial(current.targetPhoneme, chosenPhoneme, isCorrect)

        // Evaluate SM-2 quality and state
        val q = ModifiedSm2Engine.calculateQuality(isCorrect, latencySeconds, progression.consecutiveFailures)
        val nextSm2 = ModifiedSm2Engine.review(current.sm2State, q)
        progressRepository?.record(TrialRecordEntity(
            contrastId = contrast.id, targetWord = current.targetWord, selectedWord = selectedWord,
            isCorrect = isCorrect, latencyMs = (latencySeconds * 1000).toLong(),
            drillType = current.drillType.name, timestampEpochMs = Clock.System.now().toEpochMilliseconds()
        ), q)

        // Progress drills and diagnostic flags
        val feedback = progression.onTrialCompleted(isCorrect)
        val nextDrill = progression.resolveDrillType(nextSm2.masteryScore)

        _state.value = current.copy(
            selectedWord = selectedWord,
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

    override fun retryProgressSaves() { progressRepository?.retrySaves() }

    override fun setPlaybackSpeed(speed: PlaybackSpeed) {
        if (closed || _state.value.isPlayingAudio) return
        progression.setSpeedManual(speed)
        _state.value = _state.value.copy(playbackSpeed = speed)
    }

    override fun dismissFormantHint() {
        progression.dismissFormantHint()
        _state.value = _state.value.copy(showFormantHint = false)
    }

    override fun nextTrial() {
        if (closed || _state.value.lastResultCorrect == null || _state.value.isPlayingAudio) return
        ++playbackGeneration
        answerStartedAt = null
        val nextPair = contrast.pairs.random(random)
        val isWord1 = random.nextBoolean()
        val targetWord = if (isWord1) nextPair.word1 else nextPair.word2
        val targetPhoneme = if (isWord1) contrast.phoneme1 else contrast.phoneme2

        _state.value = _state.value.copy(
            currentPair = nextPair,
            targetWord = targetWord,
            targetPhoneme = targetPhoneme,
            hasPlayedPrompt = false,
            playingOptionWord = null,
            selectedWord = null,
            playbackError = null,
            lastResultCorrect = null,
            feedbackMessage = ""
        )
    }

    private fun stopPlayback() {
        ++playbackGeneration
        playbackJob?.cancel()
        playbackJob = null
        _state.value = _state.value.copy(isPlayingAudio = false, playingOptionWord = null)
        // Cleanup must finish even when the component's lifecycle scope is cancelled.
        if (stopJob?.isActive != true) {
            stopJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                withContext(NonCancellable) {
                    try {
                        audioPlayer.stop()
                    } catch (_: Exception) {
                        // Leaving must still work if the device has lost its audio session.
                    }
                }
            }
        }
    }

    override fun onBack() {
        if (closed) return
        closed = true
        stopPlayback()
        onBackClicked()
    }
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
    val isTargetPhoneme1: Boolean = true,
    val isBusy: Boolean = false,
    val isScoring: Boolean = false,
    val isPlaying: Boolean = false,
    val hasRecording: Boolean = false,
    val feedback: String? = null,
    val progressSaveError: String? = null,
    val error: String? = null
)

interface ArticulatorLabComponent {
    val state: StateFlow<ArticulatorLabUiState>
    fun retryProgressSaves() {}
    fun toggleRecording()
    fun testSynthesizedVowel(isPhoneme1: Boolean)
    fun playRecording() {}
    fun switchTargetPhoneme(isPhoneme1: Boolean)
    fun onBack()
}

class DefaultArticulatorLabComponent(
    componentContext: ComponentContext,
    contrastId: String?,
    private val scorerApi: PhonemeScorerApi,
    private val onBackClicked: () -> Unit,
    private val recorder: AudioRecorder = createAudioRecorder(),
    private val audioPlayer: AudioPlayer = createAudioPlayer(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val analysisDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val progressRepository: ProgressRepository? = null,
    private val onScored: (SpeakingRecordEntity) -> Unit = { progressRepository?.recordSpeaking(it) }
) : ArticulatorLabComponent, ComponentContext by componentContext {

    private val contrast = contrastId?.let { VowelContrastsCatalog.getContrastById(it) }
        ?: VowelContrastsCatalog.defaultContrasts.first()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _state = MutableStateFlow(ArticulatorLabUiState(contrast = contrast))
    override val state: StateFlow<ArticulatorLabUiState> = _state.asStateFlow()
    // All mutable recording data is confined to the UI dispatcher.
    private val accumulatedSamples = mutableListOf<Short>()
    private var recordedSamples = ShortArray(0)
    private var operation: Job? = null
    private var cleanup: Job? = null
    private var generation = 0L
    private var acceptingSamples = false
    private var closed = false

    init {
        progressRepository?.let { repository ->
            scope.launch {
                repository.state.collect { progress ->
                    _state.value = _state.value.copy(progressSaveError = if (progress.canRetrySave) progress.error else null)
                }
            }
        }
        scope.launch {
            recorder.state.collect { recState ->
                _state.value = _state.value.copy(recorderState = recState)
            }
        }
        lifecycle.doOnStop { stopSession() }
        lifecycle.doOnDestroy {
            closed = true
            stopSession()
            scope.cancel()
        }
    }

    private fun runOperation(block: suspend () -> Unit) {
        if (closed || operation?.isActive == true || cleanup?.isActive == true) return
        _state.value = _state.value.copy(isBusy = true, error = null)
        operation = scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                acceptingSamples = false
                generation++
                runCatching { recorder.release() }
                _state.value = _state.value.copy(error = "Unable to finish. Please try again.")
            } finally {
                _state.value = _state.value.copy(isBusy = false, isScoring = false, isPlaying = false)
            }
        }
    }

    override fun retryProgressSaves() { progressRepository?.retrySaves() }

    override fun toggleRecording(): Unit = runOperation {
        if (acceptingSamples) {
            acceptingSamples = false
            generation++
            check(recorder.stop())
            val samples = accumulatedSamples.toShortArray()
            recordedSamples = samples
            _state.value = _state.value.copy(hasRecording = samples.isNotEmpty(), isScoring = true)
            val analysis = withContext(analysisDispatcher) { LpcFormantExtractor.extract(samples) }
            if (analysis.voicedRatio == 0f || analysis.durationMs < 80) {
                _state.value = _state.value.copy(error = "No clear vowel detected. Hold the sound a little longer and try again.")
                return@runOperation
            }
            val first = _state.value.isTargetPhoneme1
            val pair = contrast.pairs.first()
            val scoring = withContext(analysisDispatcher) {
                scorerApi.scoreAudio(samples, if (first) pair.word1 else pair.word2,
                    if (first) contrast.phoneme1 else contrast.phoneme2, contrast.id)
            }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (!scoring.overallScore.isFinite() || scoring.overallScore !in 0f..100f) {
                _state.value = _state.value.copy(error = "Couldn't score this recording. Please try again.")
                return@runOperation
            }
            onScored(SpeakingRecordEntity(0, contrast.id, if (first) pair.word1 else pair.word2,
                if (first) contrast.phoneme1 else contrast.phoneme2, scoring.overallScore,
                analysis.durationMs, Clock.System.now().toEpochMilliseconds()))
            _state.value = _state.value.copy(
                liveF1 = analysis.f1Hz, liveF2 = analysis.f2Hz, liveF3 = analysis.f3Hz,
                liveDurationMs = analysis.durationMs, overallGopScore = scoring.overallScore,
                feedback = placementFeedback(analysis, first)
            )
        } else {
            accumulatedSamples.clear()
            recordedSamples = ShortArray(0)
            _state.value = ArticulatorLabUiState(contrast, recorderState = recorder.state.value,
                isTargetPhoneme1 = _state.value.isTargetPhoneme1, isBusy = true)
            if (recorder.state.value != AudioRecorderState.READY) {
                recorder.release()
                if (!recorder.prepare()) {
                    _state.value = _state.value.copy(error = "Microphone unavailable. Allow microphone access and try again.")
                    return@runOperation
                }
            }
            val token = ++generation
            acceptingSamples = true
            val started = recorder.start { buffer ->
                val copy = buffer.copyOf()
                scope.launch {
                    if (!acceptingSamples || token != generation) return@launch
                    val remaining = recorder.sampleRate * 30 - accumulatedSamples.size
                    accumulatedSamples.addAll(copy.take(remaining.coerceAtLeast(0)))
                    _state.value = _state.value.copy(liveDurationMs = accumulatedSamples.size * 1000 / recorder.sampleRate)
                    if (remaining <= copy.size) {
                        toggleRecording()
                        return@launch
                    }
                    val analysis = withContext(analysisDispatcher) { LpcFormantExtractor.extract(copy) }
                    if (acceptingSamples && token == generation && analysis.voicedRatio > 0f) {
                        _state.value = _state.value.copy(liveF1 = analysis.f1Hz,
                            liveF2 = analysis.f2Hz, liveF3 = analysis.f3Hz)
                    }
                }
            }
            if (!started) {
                acceptingSamples = false
                _state.value = _state.value.copy(error = "Could not start the microphone. Please try again.")
            }
        }
    }

    private fun placementFeedback(result: LpcFormantExtractor.FormantResult, first: Boolean): String {
        val bounds = if (first) contrast.boundsPhoneme1 else contrast.boundsPhoneme2
        val cues = mutableListOf<String>()
        if (result.f1Hz > bounds.f1MaxHz) cues += "Try raising your tongue slightly."
        else if (result.f1Hz < bounds.f1MinHz) cues += "Try lowering your tongue slightly."
        if (result.f2Hz < bounds.f2MinHz) cues += "Try moving your tongue forward."
        else if (result.f2Hz > bounds.f2MaxHz) cues += "Try moving your tongue back."
        if (bounds.minDurationMs?.let { result.durationMs < it } == true) cues += "Hold the vowel a little longer."
        if (bounds.maxDurationMs?.let { result.durationMs > it } == true) cues += "Try a shorter vowel."
        return cues.joinToString(" ").ifEmpty { "Your sound is within the target range. Try it again to build consistency." }
    }

    override fun testSynthesizedVowel(isPhoneme1: Boolean) {
        if (acceptingSamples) return
        runOperation {
            val bounds = if (isPhoneme1) contrast.boundsPhoneme1 else contrast.boundsPhoneme2
            val samples = withContext(analysisDispatcher) {
                FormantSynthesizer.synthesizeVowel(
                    f1 = (bounds.f1MinHz + bounds.f1MaxHz) / 2f,
                    f2 = (bounds.f2MinHz + bounds.f2MaxHz) / 2f,
                    durationMs = bounds.minDurationMs ?: 140)
            }
            _state.value = _state.value.copy(isPlaying = true)
            audioPlayer.play(samples)
        }
    }

    override fun playRecording() {
        if (acceptingSamples || recordedSamples.isEmpty()) return
        runOperation {
            _state.value = _state.value.copy(isPlaying = true)
            audioPlayer.play(recordedSamples)
        }
    }

    override fun switchTargetPhoneme(isPhoneme1: Boolean) {
        if (closed || acceptingSamples || _state.value.isBusy || cleanup?.isActive == true) return
        if (_state.value.isTargetPhoneme1 == isPhoneme1) return
        recordedSamples = ShortArray(0)
        _state.value = ArticulatorLabUiState(contrast, recorderState = recorder.state.value,
            isTargetPhoneme1 = isPhoneme1)
    }

    private fun stopSession() {
        acceptingSamples = false
        generation++
        val pending = operation
        pending?.cancel()
        val previousCleanup = cleanup
        cleanup = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                previousCleanup?.join()
                pending?.join()
                try {
                    audioPlayer.stop()
                } finally {
                    recorder.release()
                    _state.value = _state.value.copy(isBusy = false, isScoring = false, isPlaying = false)
                }
            }
        }
    }

    override fun onBack() {
        if (closed) return
        closed = true
        stopSession()
        onBackClicked()
    }
}

// --- Confusion Matrix Component ---
interface ConfusionMatrixComponent {
    val state: StateFlow<ProgressState>
    fun practiceContrast(id: String)
    fun practiceSpeaking(id: String)
    fun retrySaves()
    fun retryLoading()
    fun onBack()
}

class DefaultConfusionMatrixComponent(
    componentContext: ComponentContext,
    private val repository: ProgressRepository,
    private val onPractice: (String) -> Unit,
    private val onBackClicked: () -> Unit,
    private val onSpeak: (String) -> Unit
) : ConfusionMatrixComponent, ComponentContext by componentContext {
    override val state = repository.state
    override fun practiceContrast(id: String) = onPractice(id)
    override fun practiceSpeaking(id: String) = onSpeak(id)
    override fun retrySaves() = repository.retrySaves()
    override fun retryLoading() = repository.retryLoading()
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
