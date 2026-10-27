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
import com.minimalpairs.app.data.audio.PhonemeAudioRepository
import com.minimalpairs.app.core.dsp.LpcFormantExtractor
import com.minimalpairs.app.core.dsp.SpeechAudioValidation
import com.minimalpairs.app.data.remote.PracticeScoringException
import com.minimalpairs.app.core.dsp.TimeStretchProcessor
import com.minimalpairs.app.core.hal.AudioPlayer
import com.minimalpairs.app.core.hal.AudioRecorder
import com.minimalpairs.app.core.hal.AudioRecorderState
import com.minimalpairs.app.core.hal.createAudioPlayer
import com.minimalpairs.app.core.hal.createAudioRecorder
import com.minimalpairs.app.core.model.DrillType
import com.minimalpairs.app.core.model.ListeningContrast
import com.minimalpairs.app.core.model.MinimalPairWord
import com.minimalpairs.app.core.model.PlaybackSpeed
import com.minimalpairs.app.data.audio.ElevenLabsAudioRepository
import com.minimalpairs.app.data.catalog.ListeningContrastsCatalog
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
    data class ArticulatorLab(val contrastId: String? = null, val targetPhoneme: String? = null) : RootConfig

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
    fun onNavigateToArticulatorLab(contrastId: String? = null, targetPhoneme: String? = null)
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
    private val progressRepository = ProgressRepository(RoomProgressStore(progressDatabase), progressScope)
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
                    progressRepository = progressRepository,
                    initialTargetPhoneme = config.targetPhoneme
                )
            )
            is RootConfig.ConfusionMatrix -> RootChild.ConfusionMatrix(
                DefaultConfusionMatrixComponent(componentContext, progressRepository, ::onNavigateToEarTrainer, ::onBack, ::onNavigateToArticulatorLab,
                    onStory = { id ->
                        onNavigateToMeaningTrap()
                        (childStack.value.active.instance as? RootChild.MeaningTrap)?.component?.selectStory(id)
                    },
                    onRhythm = { id ->
                        onNavigateToProsody()
                        (childStack.value.active.instance as? RootChild.Prosody)?.component?.selectPattern(id)
                    })
            )
            is RootConfig.MeaningTrap -> RootChild.MeaningTrap(
                DefaultMeaningTrapComponent(
                    componentContext = componentContext,
                    confusionTracker = confusionTracker,
                    scorerApi = scorerApi,
                    onBackClicked = ::onBack,
                    progressRepository = progressRepository
                )
            )
            is RootConfig.Prosody -> RootChild.Prosody(
                DefaultProsodyComponent(componentContext, ::onBack, progressRepository = progressRepository)
            )
        }

    override fun onNavigateToDashboard() = navigation.bringToFront(RootConfig.Dashboard)
    override fun onNavigateToEarTrainer(contrastId: String) = navigation.bringToFront(RootConfig.EarTrainer(contrastId))
    override fun onNavigateToArticulatorLab(contrastId: String?, targetPhoneme: String?) {
        navigation.bringToFront(RootConfig.ArticulatorLab(contrastId, targetPhoneme))
        // A retained component may have had its target changed manually since the last visit.
        val requestedPhoneme = targetPhoneme ?: contrastId?.let { ListeningContrastsCatalog.getContrastById(it)?.phoneme1 }
        if (requestedPhoneme != null) {
            (childStack.value.active.instance as? RootChild.ArticulatorLab)?.component
                ?.preparePracticeTarget(requestedPhoneme, contrastId)
        }
    }
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
    val contrast: ListeningContrast,
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
    val error: String? = null,
    val selectedPairIndex: Int = 0,
    val scoringDescription: String? = null
) {
    val selectedPair: MinimalPairWord get() = contrast.pairs[selectedPairIndex]
}

interface ArticulatorLabComponent {
    val state: StateFlow<ArticulatorLabUiState>
    fun retryProgressSaves() {}
    fun toggleRecording()
    fun playPhonemeExample(isPhoneme1: Boolean)
    fun playRecording() {}
    fun switchTargetPhoneme(isPhoneme1: Boolean)
    fun selectContrast(contrastId: String) {}
    fun selectExamplePair(index: Int) {}
    fun preparePracticeTarget(phoneme: String, contrastId: String? = null) {
        contrastId?.let(::selectContrast)
        if (phoneme == state.value.contrast.phoneme1 || phoneme == state.value.contrast.phoneme2) {
            switchTargetPhoneme(phoneme == state.value.contrast.phoneme1)
        }
    }
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
    private val onScored: (SpeakingRecordEntity) -> Unit = { progressRepository?.recordSpeaking(it) },
    private val initialTargetPhoneme: String? = null,
    private val loadPhonemePcm: suspend (String) -> ShortArray? = PhonemeAudioRepository::loadPcm,
    private val loadWordPcm: suspend (String) -> ShortArray? = ElevenLabsAudioRepository::loadWordPcm
) : ArticulatorLabComponent, ComponentContext by componentContext {

    private var contrast = contrastId?.let { ListeningContrastsCatalog.getContrastById(it) }
        ?: ListeningContrastsCatalog.defaultContrasts.first()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _state = MutableStateFlow(ArticulatorLabUiState(contrast = contrast,
        isTargetPhoneme1 = initialTargetPhoneme != contrast.phoneme2))
    override val state: StateFlow<ArticulatorLabUiState> = _state.asStateFlow()
    // All mutable recording data is confined to the UI dispatcher.
    private val accumulatedSamples = mutableListOf<Short>()
    private var recordedSamples = ShortArray(0)
    private var operation: Job? = null
    private var cleanup: Job? = null
    private var generation = 0L
    private var frameSequence = 0L
    private var formantExpiry: Job? = null
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
            } catch (error: Exception) {
                acceptingSamples = false
                generation++
                runCatching { recorder.release() }
                _state.value = _state.value.copy(error = if (error is PracticeScoringException) error.message else "Unable to finish. Please try again.")
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
            val vowelPractice = contrast.vowelReference != null
            val analysis = if (vowelPractice) withContext(analysisDispatcher) {
                LpcFormantExtractor.extract(samples)
            } else null
            if (analysis != null && (analysis.voicedRatio == 0f || analysis.durationMs < 80)) {
                _state.value = _state.value.copy(liveF1 = 0f, liveF2 = 0f, liveF3 = 0f,
                    error = "No clear vowel detected. Hold the sound a little longer and try again.")
                return@runOperation
            }
            if (!vowelPractice && !withContext(analysisDispatcher) { SpeechAudioValidation.hasUsableAudio(samples) }) {
                _state.value = _state.value.copy(error = "No clear sound detected. Record the whole word and try again.")
                return@runOperation
            }
            val durationMs = analysis?.durationMs ?: (samples.size * 1000 / recorder.sampleRate)
            _state.value = _state.value.copy(liveF1 = analysis?.f1Hz ?: 0f, liveF2 = analysis?.f2Hz ?: 0f,
                liveF3 = analysis?.f3Hz ?: 0f, liveDurationMs = durationMs)
            val first = _state.value.isTargetPhoneme1
            val pair = _state.value.selectedPair
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
                durationMs, Clock.System.now().toEpochMilliseconds(), scoringMethod = scoring.scoringMethod))
            _state.value = _state.value.copy(
                liveF1 = analysis?.f1Hz ?: 0f, liveF2 = analysis?.f2Hz ?: 0f, liveF3 = analysis?.f3Hz ?: 0f,
                liveDurationMs = durationMs, overallGopScore = scoring.overallScore,
                feedback = if (analysis != null) placementFeedback(analysis, first) else scoring.feedback,
                scoringDescription = scoring.scoringDescription
            )
        } else {
            accumulatedSamples.clear()
            recordedSamples = ShortArray(0)
            _state.value = ArticulatorLabUiState(contrast, recorderState = recorder.state.value,
                isTargetPhoneme1 = _state.value.isTargetPhoneme1, isBusy = true,
                selectedPairIndex = _state.value.selectedPairIndex,
                progressSaveError = _state.value.progressSaveError)
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
                    if (contrast.vowelReference == null) return@launch
                    val frame = ++frameSequence
                    val analysis = withContext(analysisDispatcher) { LpcFormantExtractor.extract(copy) }
                    if (acceptingSamples && token == generation && frame == frameSequence) {
                        val voiced = analysis.voicedRatio > 0f
                        _state.value = _state.value.copy(liveF1 = if (voiced) analysis.f1Hz else 0f,
                            liveF2 = if (voiced) analysis.f2Hz else 0f,
                            liveF3 = if (voiced) analysis.f3Hz else 0f)
                        formantExpiry?.cancel()
                        formantExpiry = scope.launch {
                            kotlinx.coroutines.delay(750)
                            if (acceptingSamples && token == generation && frame == frameSequence) {
                                _state.value = _state.value.copy(liveF1 = 0f, liveF2 = 0f, liveF3 = 0f)
                            }
                        }
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
        val vowel = checkNotNull(contrast.vowelReference)
        val bounds = if (first) vowel.boundsPhoneme1 else vowel.boundsPhoneme2
        val cues = mutableListOf<String>()
        if (result.f1Hz > bounds.f1MaxHz) cues += "Try raising your tongue slightly."
        else if (result.f1Hz < bounds.f1MinHz) cues += "Try lowering your tongue slightly."
        if (result.f2Hz < bounds.f2MinHz) cues += "Try moving your tongue forward."
        else if (result.f2Hz > bounds.f2MaxHz) cues += "Try moving your tongue back."
        if (bounds.minDurationMs?.let { result.durationMs < it } == true) cues += "Hold the vowel a little longer."
        if (bounds.maxDurationMs?.let { result.durationMs > it } == true) cues += "Try a shorter vowel."
        return cues.joinToString(" ").ifEmpty { "Your sound is within the target range. Try it again to build consistency." }
    }

    override fun playPhonemeExample(isPhoneme1: Boolean) {
        if (acceptingSamples) return
        runOperation {
            val phoneme = if (isPhoneme1) contrast.phoneme1 else contrast.phoneme2
            val pair = _state.value.selectedPair
            val samples = withContext(analysisDispatcher) {
                if (contrast.vowelReference != null) loadPhonemePcm(phoneme)
                else loadWordPcm(if (isPhoneme1) pair.word1 else pair.word2)
            }
            if (samples == null || samples.isEmpty()) {
                _state.value = _state.value.copy(error = "The example sound is unavailable. Please try again.")
                return@runOperation
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

    override fun preparePracticeTarget(phoneme: String, contrastId: String?) {
        val requestedContrast = contrastId?.let { ListeningContrastsCatalog.getContrastById(it) } ?: contrast
        if (closed || (phoneme != requestedContrast.phoneme1 && phoneme != requestedContrast.phoneme2)) return
        scope.launch {
            cleanup?.join()
            if (!closed) {
                selectContrast(requestedContrast.id)
                switchTargetPhoneme(phoneme == requestedContrast.phoneme1)
            }
        }
    }

    private fun canChangeSelection(): Boolean =
        !closed && !acceptingSamples && !_state.value.isBusy && cleanup?.isActive != true

    private fun resetPracticeSelection(first: Boolean, pairIndex: Int) {
        accumulatedSamples.clear()
        recordedSamples = ShortArray(0)
        _state.value = ArticulatorLabUiState(contrast, recorderState = recorder.state.value,
            isTargetPhoneme1 = first, selectedPairIndex = pairIndex,
            progressSaveError = _state.value.progressSaveError)
    }

    override fun selectContrast(contrastId: String) {
        if (!canChangeSelection() || contrast.id == contrastId) return
        val selected = ListeningContrastsCatalog.getContrastById(contrastId) ?: return
        contrast = selected
        resetPracticeSelection(first = true, pairIndex = 0)
    }

    override fun selectExamplePair(index: Int) {
        if (!canChangeSelection() || index !in contrast.pairs.indices || index == _state.value.selectedPairIndex) return
        resetPracticeSelection(_state.value.isTargetPhoneme1, index)
    }

    override fun switchTargetPhoneme(isPhoneme1: Boolean) {
        if (!canChangeSelection() || _state.value.isTargetPhoneme1 == isPhoneme1) return
        resetPracticeSelection(isPhoneme1, _state.value.selectedPairIndex)
    }

    private fun stopSession() {
        if (acceptingSamples) {
            _state.value = _state.value.copy(liveF1 = 0f, liveF2 = 0f, liveF3 = 0f)
        }
        acceptingSamples = false
        formantExpiry?.cancel()
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
    fun practiceSpeaking(id: String, phoneme: String)
    fun startSpeaking()
    fun practiceStory(id: String) {}
    fun practiceRhythm(id: String) {}
    suspend fun exportProgress(): ProgressSnapshot = error("Progress export is unavailable")
    suspend fun importProgress(snapshot: ProgressSnapshot) { error("Progress import is unavailable") }
    suspend fun resetProgress() { error("Progress reset is unavailable") }
    fun setDateRange(range: ProgressDateRange) {}
    fun setContrastFilter(contrastId: String?) {}
    fun clearFilters() {}
    fun retrySaves()
    fun retryLoading()
    fun onBack()
}

class DefaultConfusionMatrixComponent(
    componentContext: ComponentContext,
    private val repository: ProgressRepository,
    private val onPractice: (String) -> Unit,
    private val onBackClicked: () -> Unit,
    private val onSpeak: (String?, String?) -> Unit,
    private val onStory: (String) -> Unit = {},
    private val onRhythm: (String) -> Unit = {}
) : ConfusionMatrixComponent, ComponentContext by componentContext {
    override val state = repository.state
    override fun practiceContrast(id: String) = onPractice(id)
    override fun practiceSpeaking(id: String, phoneme: String) = onSpeak(id, phoneme)
    override fun startSpeaking() = onSpeak(null, null)
    override fun practiceStory(id: String) = onStory(id)
    override fun practiceRhythm(id: String) = onRhythm(id)
    override suspend fun exportProgress() = repository.exportSnapshot()
    override suspend fun importProgress(snapshot: ProgressSnapshot) = repository.importSnapshot(snapshot)
    override suspend fun resetProgress() = repository.resetProgress()
    override fun setDateRange(range: ProgressDateRange) = repository.setDateRange(range)
    override fun setContrastFilter(contrastId: String?) = repository.setContrastFilter(contrastId)
    override fun clearFilters() = repository.clearFilters()
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
    private val audioPlayer: AudioPlayer = createAudioPlayer(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val analysisDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val progressRepository: ProgressRepository? = null,
    private val onScored: (ModulePracticeRecordEntity) -> Unit = { progressRepository?.recordModule(it) }
) : MeaningTrapComponent, ComponentContext by componentContext {

    override val availableStories: List<com.minimalpairs.app.core.model.ComicStory> =
        com.minimalpairs.app.data.catalog.MeaningTrapCatalog.stories

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(
        MeaningTrapUiState(activeStory = availableStories.first())
    )
    override val state: StateFlow<MeaningTrapUiState> = _state.asStateFlow()

    private val playback = ModulePlaybackSession(scope, audioPlayer) { playing ->
        _state.value = _state.value.copy(isPlayingAudio = playing)
    }
    private val recording = ModuleRecordingSession(scope, recorder,
        onRecording = { active -> _state.value = _state.value.copy(isRecording = active) },
        onError = { message -> _state.value = _state.value.copy(feedbackText = message) },
        stopPlayback = playback::stop)

    init {
        lifecycle.doOnStop { recording.cancel() }
        lifecycle.doOnDestroy { recording.cancel(); scope.cancel() }
    }

    override fun selectStory(storyId: String) {
        val story = availableStories.firstOrNull { it.id == storyId } ?: return
        recording.cancel()
        _state.value = MeaningTrapUiState(activeStory = story)
        playPromptAudio()
    }

    private fun playStoryAudio(load: suspend () -> ShortArray?) {
        playback.play({ !_state.value.isRecording && !recording.busy }, load)
    }

    override fun playPromptAudio() {
        val storyId = _state.value.activeStory.id
        playStoryAudio { ElevenLabsAudioRepository.loadMeaningTrapPromptPcm(storyId) }
    }

    override fun playTargetPhraseAudio() {
        val storyId = _state.value.activeStory.id
        playStoryAudio { ElevenLabsAudioRepository.loadMeaningTrapPhrasePcm(storyId) }
    }

    override fun playOutcomeAudio() {
        val branch = _state.value.branch
        if (branch == com.minimalpairs.app.core.model.MeaningTrapBranch.PENDING) return
        val storyId = _state.value.activeStory.id
        val isSuccess = branch == com.minimalpairs.app.core.model.MeaningTrapBranch.INTENDED_SUCCESS
        playStoryAudio { ElevenLabsAudioRepository.loadMeaningTrapOutcomePcm(storyId, isSuccess) }
    }

    override fun toggleRecording() {
        if (recording.busy) return
        val story = _state.value.activeStory
        if (!_state.value.isRecording) _state.value = _state.value.copy(
            branch = com.minimalpairs.app.core.model.MeaningTrapBranch.PENDING,
            gopScore = null, feedbackText = "Listening… Speak the phrase clearly.")
        recording.toggle { samples ->
            val durationMs = samples.size * 1000 / recorder.sampleRate
            val voiced = withContext(analysisDispatcher) {
                com.minimalpairs.app.core.dsp.PitchTracker.trackPitch(samples).any { it.isVoiced }
            }
            if (!voiced || durationMs < 80) {
                _state.value = _state.value.copy(feedbackText = "No clear voice detected. Try the phrase again.")
                return@toggle
            }
            val result = withContext(analysisDispatcher) {
                scorerApi.scoreAudio(samples, story.intendedWord, story.intendedIpa, story.contrastId)
            }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (!result.overallScore.isFinite() || result.overallScore !in 0f..100f) {
                _state.value = _state.value.copy(feedbackText = "Couldn't score this recording. Please try again.")
                return@toggle
            }
            val success = result.overallScore >= 70f
            onScored(ModulePracticeRecordEntity(0, "STORIES", story.id, story.title, story.contrastId,
                result.overallScore, success, durationMs, Clock.System.now().toEpochMilliseconds()))
            applyEvaluationResult(success, result.overallScore)
        }
    }

    override fun triggerSimulatedOutcome(isCorrect: Boolean) {
        recording.cancel()
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
        recording.cancel()
        _state.value = _state.value.copy(
            branch = com.minimalpairs.app.core.model.MeaningTrapBranch.PENDING,
            gopScore = null,
            feedbackText = ""
        )
    }

    override fun onBack() { recording.cancel(); onBackClicked() }
}

// --- Module 4: Prosody Component ---
data class ProsodyUiState(
    val activePattern: com.minimalpairs.app.core.model.ProsodyPattern,
    val isRecording: Boolean = false,
    val isPlayingReference: Boolean = false,
    val evaluation: com.minimalpairs.app.core.dsp.ProsodyEvaluation? = null,
    val error: String? = null
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
    private val audioPlayer: AudioPlayer = createAudioPlayer(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val analysisDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val progressRepository: ProgressRepository? = null,
    private val onScored: (ModulePracticeRecordEntity) -> Unit = { progressRepository?.recordModule(it) }
) : ProsodyComponent, ComponentContext by componentContext {

    override val availablePatterns: List<com.minimalpairs.app.core.model.ProsodyPattern> =
        com.minimalpairs.app.data.catalog.ProsodyCatalog.patterns

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(
        ProsodyUiState(activePattern = availablePatterns.first())
    )
    override val state: StateFlow<ProsodyUiState> = _state.asStateFlow()

    private val playback = ModulePlaybackSession(scope, audioPlayer) { playing ->
        _state.value = _state.value.copy(isPlayingReference = playing)
    }
    private val recording = ModuleRecordingSession(scope, recorder,
        onRecording = { active -> _state.value = _state.value.copy(isRecording = active) },
        onError = { message -> _state.value = _state.value.copy(error = message) },
        stopPlayback = playback::stop)

    init {
        lifecycle.doOnStop { recording.cancel() }
        lifecycle.doOnDestroy { recording.cancel(); scope.cancel() }
    }

    override fun selectPattern(patternId: String) {
        val pattern = availablePatterns.firstOrNull { it.id == patternId } ?: return
        recording.cancel()
        _state.value = ProsodyUiState(activePattern = pattern)
    }

    override fun playReferenceAudio() {
        val pattern = _state.value.activePattern
        playback.play({ !_state.value.isRecording && !recording.busy }) {
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

            speechSamples
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
        if (recording.busy) return
        val pattern = _state.value.activePattern
        if (!_state.value.isRecording) _state.value = _state.value.copy(evaluation = null, error = null)
        recording.toggle { samples ->
            val durationMs = samples.size * 1000 / recorder.sampleRate
            val frames = withContext(analysisDispatcher) { com.minimalpairs.app.core.dsp.PitchTracker.trackPitch(samples) }
            if (durationMs < 80 || frames.none { it.isVoiced }) {
                _state.value = _state.value.copy(error = "No clear voice detected. Try the sentence again.")
                return@toggle
            }
            val eval = withContext(analysisDispatcher) {
                com.minimalpairs.app.core.dsp.PitchTracker.evaluateProsody(frames, pattern.referenceContour)
            }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (!eval.overallScore.isFinite() || eval.overallScore !in 0f..100f) {
                _state.value = _state.value.copy(error = "Couldn't evaluate this recording. Please try again.")
                return@toggle
            }
            onScored(ModulePracticeRecordEntity(0, "RHYTHM", pattern.id, pattern.sentenceText, null,
                eval.overallScore, eval.overallScore >= 70f, durationMs, Clock.System.now().toEpochMilliseconds()))
            _state.value = _state.value.copy(evaluation = eval, error = null)
        }
    }

    override fun triggerSimulatedProsody(isGoodMatch: Boolean) {
        recording.cancel()
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
        recording.cancel()
        _state.value = _state.value.copy(evaluation = null)
    }

    override fun onBack() { recording.cancel(); onBackClicked() }
}
