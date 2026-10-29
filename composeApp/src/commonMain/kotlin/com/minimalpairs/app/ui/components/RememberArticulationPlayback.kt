package com.minimalpairs.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.minimalpairs.app.ui.reference.ArticulationClip
import com.minimalpairs.app.ui.reference.ArticulationPlaybackState
import com.minimalpairs.app.ui.reference.ArticulationReviewStatus
import com.minimalpairs.app.ui.reference.ArticulationSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Opt-in adapter. A null/unreviewed specification leaves the legacy static reference active. */
@Composable
fun rememberArticulationPlayback(
    phoneme: String,
    spec: ArticulationSpec?,
    retry: Int = 0,
    readAsset: suspend (String) -> ByteArray
): MutableState<ArticulationPlaybackState> {
    // Recreate synchronously: a previous sound must not appear for even one composition.
    val state = remember(phoneme, spec, retry) { mutableStateOf(ArticulationPlaybackState().select(phoneme)) }
    val latestReader = rememberUpdatedState(readAsset)
    if (spec == null || spec.phoneme != phoneme || spec.reviewStatus != ArticulationReviewStatus.ACCEPTED) return state
    LaunchedEffect(state) {
        val token = state.value.generation
        try {
            val clip = withContext(Dispatchers.Default) {
                val assets = spec.phases.map { latestReader.value(it.assetPath) }
                ensureActive()
                ArticulationClip.decode(spec, assets)
            }
            state.value = state.value.loaded(token, clip)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            state.value = state.value.loadFailed(token)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { state.value = state.value.pause() }
    LaunchedEffect(state, state.value.generation, state.value.playing) {
        if (!state.value.playing) return@LaunchedEffect
        val token = state.value.generation
        val start = withFrameNanos { it }
        var advancedMs = 0L
        while (state.value.playing && state.value.generation == token) {
            val now = withFrameNanos { it }
            val totalMs = ((now - start).coerceAtLeast(0) / 1_000_000L)
            val delta = (totalMs - advancedMs).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
            state.value = state.value.advance(token, delta)
            advancedMs += delta
        }
    }
    return state
}
