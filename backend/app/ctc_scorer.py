"""Local phoneme-CTC inference and Viterbi alignment; no network model downloads.

Scores are aligned model posterior confidence, not calibrated human GOP ratings.
"""
import json
import os
from functools import lru_cache
from pathlib import Path
from threading import Lock

import numpy as np


class ModelUnavailable(RuntimeError):
    pass


class InvalidTarget(ValueError):
    pass


class RecordingQualityError(ValueError):
    def __init__(self, code, message):
        super().__init__(message)
        self.code = code


def prepare_word_audio(samples):
    """Conservative single-word endpointing, with fixed 150ms model context.

    This energy gate is not a speech recognizer or a calibrated SNR estimator.
    Reject poor input rather than treating microphone conditions as pronunciation.
    Return the crop's (possibly negative, for padding) original sample offset.
    """
    samples = np.asarray(samples, dtype=np.float32)
    if samples.ndim != 1 or len(samples) < 1280 or not np.isfinite(samples).all():
        raise RecordingQualityError("no_clear_speech", "Record one complete word clearly.")
    if np.mean(np.abs(samples) >= .999) > .01:
        raise RecordingQualityError("clipped_audio", "The microphone is clipping. Move farther away or lower its input level.")
    hop, window, context = 160, 320, 2400
    frames = np.lib.stride_tricks.sliding_window_view(samples, window)[::hop]
    energy = np.std(frames, axis=1)  # Ignore DC offset when locating activity.
    peak, floor = float(energy.max()), float(np.quantile(energy, .2))
    if peak < .003:
        raise RecordingQualityError("no_clear_speech", "No clear voice was captured. Check the microphone and record again.")
    if peak < 20 * max(floor, 1e-8):
        raise RecordingQualityError("noisy_audio", "Background noise is too strong for a reliable score. Move closer and try in a quieter place.")
    active = np.flatnonzero(energy > max(peak * .06, floor * 3))
    if len(active) < 6:
        raise RecordingQualityError("no_clear_speech", "Record one complete word clearly.")
    # Anchor on sustained activity, not isolated taps or weak room sounds.
    # Expand at the lower threshold to retain quiet fricatives/releases.
    strong = np.flatnonzero(energy > max(peak * .2, floor * 3))
    groups = np.split(strong, np.flatnonzero(np.diff(strong) > 5) + 1)
    cores = [group for group in groups if len(group) >= 6]
    if not cores:
        raise RecordingQualityError("no_clear_speech", "Record one complete word clearly.")
    if any(right[0] - left[-1] > 65 for left, right in zip(cores, cores[1:])):
        raise RecordingQualityError("multiple_utterances", "Say the selected word once, without other speech.")
    first, last = int(cores[0][0]), int(cores[-1][-1])
    for frame in active[active < first][::-1]:
        if first - frame > 10:
            break
        first = int(frame)
    for frame in active[active > last]:
        if frame - last > 10:
            break
        last = int(frame)
    if (last - first) * hop > 48000:
        raise RecordingQualityError("multiple_utterances", "Say the selected word once, without other speech.")
    start = first * hop - context
    end = last * hop + window + context
    audio = np.pad(samples[max(0, start):min(len(samples), end)],
                   (max(0, -start), max(0, end - len(samples))),
                   constant_values=float(np.median(samples)))
    audio = audio - audio.mean()
    audio = audio * (.1 / max(float(np.sqrt(np.mean(audio ** 2))), 1e-8))
    return audio.astype(np.float32), start


def align_posteriors(logits, targets, blank_id, duration, *, segment_scores=False, selected_index=None):
    """Return per-target posterior confidence and true CTC frame intervals.

    Expanded states alternate blank and target; repeated adjacent targets must
    pass through a blank. No equal-duration slicing or fabricated alignment.
    """
    logits = np.asarray(logits, dtype=np.float64)
    if logits.ndim != 2 or not logits.size or not np.isfinite(logits).all():
        raise ValueError("Invalid model emissions")
    frames, vocabulary = logits.shape
    if not targets or len(targets) > 128 or any(t == blank_id or t < 0 or t >= vocabulary for t in targets):
        raise InvalidTarget("Unsupported or empty phoneme target")
    if not 0 <= blank_id < vocabulary or not np.isfinite(duration) or duration <= 0:
        raise ValueError("Invalid alignment metadata")
    required_frames = len(targets) + sum(a == b for a, b in zip(targets, targets[1:]))
    if frames < required_frames:
        raise InvalidTarget("Recording is too short to align the requested phonemes")
    shifted = logits - logits.max(axis=1, keepdims=True)
    logp = shifted - np.log(np.exp(shifted).sum(axis=1, keepdims=True))
    states = [blank_id]
    for target in targets:
        states.extend((target, blank_id))
    scores = np.full(len(states), -np.inf)
    scores[0] = logp[0, blank_id]
    scores[1] = logp[0, targets[0]]
    back = np.zeros((frames, len(states)), dtype=np.int16)
    for time in range(1, frames):
        next_scores = np.full(len(states), -np.inf)
        for state, token in enumerate(states):
            previous = [state]
            if state > 0:
                previous.append(state - 1)
            if state > 1 and token != blank_id and token != states[state - 2]:
                previous.append(state - 2)
            best = max(previous, key=lambda index: scores[index])
            next_scores[state] = scores[best] + logp[time, token]
            back[time, state] = best
        scores = next_scores
    state = max((len(states) - 1, len(states) - 2), key=lambda index: scores[index])
    if not np.isfinite(scores[state]):
        raise InvalidTarget("The requested sequence could not be aligned")
    path = [state]
    for time in range(frames - 1, 0, -1):
        state = int(back[time, state])
        path.append(state)
    path = np.asarray(path[::-1])
    # A single target spike must not hide a sustained competing phoneme that
    # forced alignment pushes into blank states. Penalize sequence mismatch on
    # frames whose greedy prediction is speech; silence cannot dilute it.
    speech_frames = np.argmax(logp, axis=1) != blank_id
    if not speech_frames.any():
        raise InvalidTarget("The model did not detect a phoneme in this recording")
    aligned_tokens = np.asarray(states)[path]
    aligned_logp = logp[np.arange(frames), aligned_tokens]
    sequence_agreement = float(np.exp((aligned_logp[speech_frames] - logp.max(axis=1)[speech_frames]).mean()))
    results = []
    token_times = [np.flatnonzero(path == index * 2 + 1) for index in range(len(targets))]
    for index, target in enumerate(targets):
        times = np.flatnonzero(path == index * 2 + 1)
        if not times.size:
            raise InvalidTarget("A target phoneme has no aligned frames")
        confidence = float(np.exp(logp[times, target].mean()) * sequence_agreement)
        if segment_scores:
            # Split inter-token blank gaps halfway. Include local competing speech
            # so a single forced target spike cannot hide a substitution. Other
            # phonemes' confidence never enters this selected segment's score.
            left = 0 if index == 0 else (token_times[index - 1][-1] + times[0] + 1) // 2
            right = frames if index == len(targets) - 1 else (times[-1] + token_times[index + 1][0] + 1) // 2
            local = np.arange(left, right)
            evaluated = np.union1d(times, local[speech_frames[local]])
            confidence = float(np.exp(logp[evaluated, target].mean()))
            if index == selected_index:
                evidence = local[speech_frames[local]]
                if not len(evidence) or float(np.exp(logp[evidence].max(axis=1)).max()) < .6:
                    raise RecordingQualityError("uncertain_alignment", "The model could not identify the selected sound reliably. Record the word again.")
        results.append({
            "gop_score": float(100 * confidence),
            "start_time": float(times[0] * duration / frames),
            "end_time": float((times[-1] + 1) * duration / frames),
        })
    return results


class LocalPhonemeModel:
    def __init__(self, directory):
        path = Path(directory)
        if not path.is_dir():
            raise ModelUnavailable("PHONEME_MODEL_DIR must name a local model directory")
        try:
            # Optional dependencies are imported only when model scoring is enabled.
            import torch
            from transformers import AutoFeatureExtractor, AutoModelForCTC
            self.torch = torch
            self.extractor = AutoFeatureExtractor.from_pretrained(str(path), local_files_only=True, trust_remote_code=False)
            self.model = AutoModelForCTC.from_pretrained(str(path), local_files_only=True, trust_remote_code=False)
            self.model.eval()
            self.vocab = json.loads((path / "vocab.json").read_text())
            self.blank_id = self.model.config.pad_token_id
            if self.extractor.sampling_rate != 16000 or self.blank_id is None:
                raise ValueError("Expected a 16kHz phoneme CTC model with a blank token")
            # Reject character-ASR checkpoints, which cannot evaluate the app vowels.
            if not all(token in self.vocab for token in ("iː", "ɪ", "æ", "ʊ", "ɒ", "ɔː")):
                raise ValueError("Model vocabulary must contain IPA phoneme tokens")
            if any(not isinstance(index, int) or index < 0 or index >= self.model.config.vocab_size
                   for index in self.vocab.values()) or len(set(self.vocab.values())) != len(self.vocab):
                raise ValueError("Invalid model vocabulary indices")
            self.model_id = os.environ.get("PHONEME_MODEL_ID", path.name)
            self.lock = Lock()
        except Exception as error:
            raise ModelUnavailable("Could not load local phoneme model; check dependencies and model files") from error

    def infer_logits(self, samples):
        """Return unchanged checkpoint emissions for scoring and offline evaluation."""
        with self.lock, self.torch.inference_mode():
            features = self.extractor(samples, sampling_rate=16000, return_tensors="pt")
            logits = self.model(**features).logits[0].cpu().numpy()
            if logits.ndim != 2 or logits.shape[1] != self.model.config.vocab_size:
                raise ModelUnavailable("Model emissions do not match its vocabulary")
        return logits

    def score(self, samples, phonemes, target_phoneme_index=None):
        # Independent isolated-nucleus evaluation found this checkpoint largely
        # blank and unreliable. Do not turn its occasional token spike into a
        # learner grade. The app can explicitly fall back to acoustic estimates.
        if len(phonemes) == 1:
            raise InvalidTarget("This model does not support isolated-phoneme scoring; use the acoustic estimate")
        # App DRESS /e/ is British broad transcription for [ɛ]. Keep the public
        # label while using the correct narrow model token.
        if target_phoneme_index is not None and not 0 <= target_phoneme_index < len(phonemes):
            raise InvalidTarget("Target phoneme index is outside the word transcript")
        aliases = {"e": "ɛ", "r": "ɹ", "ɪə": "iə"}
        model_tokens = [aliases.get(token, token) for token in phonemes]
        if any(token not in self.vocab or token.startswith("<") for token in model_tokens):
            raise InvalidTarget("An IPA token is not supported by this model")
        targets = [self.vocab[token] for token in model_tokens]
        duration = len(samples) / 16000
        offset = 0
        if target_phoneme_index is not None:
            samples, offset = prepare_word_audio(samples)
        logits = self.infer_logits(samples)
        aligned = align_posteriors(logits, targets, self.blank_id, len(samples) / 16000,
                                   segment_scores=target_phoneme_index is not None,
                                   selected_index=target_phoneme_index)
        for score in aligned:
            score["start_time"] = max(0., score["start_time"] + offset / 16000)
            score["end_time"] = min(duration, score["end_time"] + offset / 16000)
            if score["end_time"] <= score["start_time"]:
                raise RecordingQualityError("uncertain_alignment", "The model could not locate the sound inside the recording.")
        return [dict(phoneme=phoneme, **score) for phoneme, score in zip(phonemes, aligned)]


_load_lock = Lock()


@lru_cache(maxsize=1)
def _load_model(directory):
    return LocalPhonemeModel(directory)


def model_is_loaded():
    return bool(_load_model.cache_info().currsize)


def get_model():
    directory = os.environ.get("PHONEME_MODEL_DIR")
    if not directory:
        raise ModelUnavailable("Neural scoring is not configured; set PHONEME_MODEL_DIR to a local phoneme model")
    # The lock surrounds the cache lookup: simultaneous first requests must not
    # each allocate a large checkpoint before lru_cache has stored the result.
    with _load_lock:
        return _load_model(directory)
