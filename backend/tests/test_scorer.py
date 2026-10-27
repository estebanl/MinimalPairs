import io
import wave
from unittest.mock import patch

import numpy as np
import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.ctc_scorer import ModelUnavailable, InvalidTarget, align_posteriors, _load_model

client = TestClient(app)


def generate_test_wav(duration_sec=.25, sample_rate=16000, channels=1, silent=False):
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as wav:
        wav.setnchannels(channels)
        wav.setsampwidth(2)
        wav.setframerate(sample_rate)
        samples = np.sin(2 * np.pi * 440 * np.arange(int(sample_rate * duration_sec)) / sample_rate)
        wav.writeframes((samples * (0 if silent else 10000)).astype("<i2").tobytes() * channels)
    return buffer.getvalue()


def post_audio(audio=None, ipa="ʃ iː p"):
    return client.post("/v1/score/phoneme", data={"target_transcript": "sheep", "expected_ipa": ipa},
                       files={"audio_file": ("test.wav", audio if audio is not None else generate_test_wav(), "audio/wav")})


class FakeModel:
    model_id = "deterministic-test-emissions"

    def score(self, samples, phonemes):
        assert samples.dtype == np.float32
        assert len(samples) == 4000
        targets = list(range(1, len(phonemes) + 1))
        logits = np.full((7, 4), -5.)
        for time, token in enumerate((0, 1, 0, 2, 2, 3, 0)):
            logits[time, token] = 5
        return [dict(phoneme=phoneme, **result) for phoneme, result in
                zip(phonemes, align_posteriors(logits, targets, 0, len(samples) / 16000))]


def test_health_separates_liveness_from_model_availability(monkeypatch):
    monkeypatch.delenv("PHONEME_MODEL_DIR", raising=False)
    _load_model.cache_clear()
    body = client.get("/health").json()
    assert body == {"status": "ok", "service": "phoneme-scorer", "model_status": "unavailable"}


def test_score_uses_inference_emissions_and_ctc_alignment():
    with patch("app.main.get_model", return_value=FakeModel()):
        response = post_audio()
    assert response.status_code == 200
    body = response.json()
    assert body["scoring_method"] == "neural_ctc"
    assert body["model_id"] == "deterministic-test-emissions"
    assert body["phonemes"][1]["phoneme"] == "iː"
    assert body["phonemes"][1]["start_time"] == pytest.approx(3 * .25 / 7)
    assert body["phonemes"][1]["end_time"] == pytest.approx(5 * .25 / 7)
    assert body["overall_score"] > 99


def test_unconfigured_model_is_unavailable_not_a_fabricated_score(monkeypatch):
    monkeypatch.delenv("PHONEME_MODEL_DIR", raising=False)
    _load_model.cache_clear()
    assert post_audio().status_code == 503


@pytest.mark.parametrize("audio", [b"bad-wav", generate_test_wav(sample_rate=8000),
    generate_test_wav(channels=2), generate_test_wav(duration_sec=.03), generate_test_wav(silent=True),
    generate_test_wav()[:-200]])
def test_reject_invalid_audio_before_model_runs(audio):
    with patch("app.main.get_model") as model:
        assert post_audio(audio).status_code == 400
        model.assert_not_called()


def test_upload_size_limit():
    assert post_audio(b"x" * 1_000_001).status_code == 413


def test_blank_ipa_rejected():
    assert post_audio(ipa=" / ").status_code == 400


def test_unsupported_ipa_is_client_error():
    with patch("app.main.get_model") as model:
        model.return_value.score.side_effect = InvalidTarget("Unsupported IPA")
        assert post_audio().status_code == 400


def test_isolated_task_is_rejected_before_loading_with_explanation(monkeypatch):
    monkeypatch.delenv("PHONEME_MODEL_DIR", raising=False)
    with patch("app.main.get_model") as loader:
        response = post_audio(ipa="iː")
    assert response.status_code == 400
    assert "does not support isolated-phoneme scoring" in response.json()["detail"]
    loader.assert_not_called()


def test_model_failure_is_recoverable_service_error():
    with patch("app.main.get_model") as model:
        model.return_value.score.side_effect = RuntimeError("private internals")
        response = post_audio()
    assert response.status_code == 503
    assert "private internals" not in response.text


def test_ctc_alignment_requires_blank_between_repeated_targets():
    logits = np.array([[0., 8.], [8., 0.], [0., 8.]])
    results = align_posteriors(logits, [1, 1], 0, .3)
    assert results[0]["end_time"] == pytest.approx(.1)
    assert results[1]["start_time"] == pytest.approx(.2)
    with pytest.raises(InvalidTarget):
        align_posteriors(logits[:2], [1, 1], 0, .2)


def test_wrong_phoneme_receives_lower_posterior_score():
    logits = np.array([[8., 0., 0.], [0., 8., 0.], [0., 8., 0.], [8., 0., 0.]])
    correct = align_posteriors(logits, [1], 0, .4)[0]["gop_score"]
    wrong = align_posteriors(logits, [2], 0, .4)[0]["gop_score"]
    assert correct > 99
    assert wrong < 1


@pytest.mark.parametrize("logits", [np.array([[np.nan, 0.]]), np.array([]), np.array([1., 2.])])
def test_invalid_model_emissions_rejected(logits):
    with pytest.raises(ValueError):
        align_posteriors(logits, [1], 0, .2)


def test_target_spike_cannot_hide_sustained_wrong_phoneme():
    logits = np.full((20, 3), -5.)
    logits[:, 2] = 5.
    logits[10] = [-5., 5., -5.]
    score = align_posteriors(logits, [1], 0, .4)[0]["gop_score"]
    assert score < 1


def test_local_model_loader_never_downloads_and_checks_phoneme_vocabulary(tmp_path, monkeypatch):
    import json
    import sys
    from types import SimpleNamespace
    from app.ctc_scorer import LocalPhonemeModel

    (tmp_path / "vocab.json").write_text(json.dumps({"<pad>": 0, "iː": 1, "ɪ": 2, "æ": 3, "ʊ": 4, "ɒ": 5, "ɔː": 6}))
    calls = []

    class Loader:
        @classmethod
        def from_pretrained(cls, directory, **kwargs):
            calls.append((directory, kwargs))
            return SimpleNamespace(sampling_rate=16000, config=SimpleNamespace(pad_token_id=0, vocab_size=7), eval=lambda: None)

    monkeypatch.setitem(sys.modules, "torch", SimpleNamespace())
    monkeypatch.setitem(sys.modules, "transformers", SimpleNamespace(AutoFeatureExtractor=Loader, AutoModelForCTC=Loader))
    model = LocalPhonemeModel(tmp_path)
    assert model.blank_id == 0
    assert len(calls) == 2
    assert all(kwargs == {"local_files_only": True, "trust_remote_code": False} for _, kwargs in calls)
    with patch.object(model, "infer_logits") as inference:
        with pytest.raises(InvalidTarget, match="does not support isolated-phoneme"):
            model.score(np.ones(4000, dtype=np.float32), ["iː"])
        inference.assert_not_called()
    (tmp_path / "vocab.json").write_text(json.dumps({"a": 0, "b": 1}))
    with pytest.raises(ModelUnavailable):
        LocalPhonemeModel(tmp_path)


def test_simultaneous_first_requests_share_one_model_allocation(monkeypatch):
    from concurrent.futures import ThreadPoolExecutor
    from threading import Event
    from app.ctc_scorer import get_model

    monkeypatch.setenv("PHONEME_MODEL_DIR", "/test/model")
    _load_model.cache_clear()
    entered = Event()
    release = Event()
    model = object()

    def load(directory):
        assert directory == "/test/model"
        entered.set()
        assert release.wait(timeout=5)
        return model

    try:
        with patch("app.ctc_scorer.LocalPhonemeModel", side_effect=load) as loader:
            with ThreadPoolExecutor(max_workers=8) as executor:
                futures = [executor.submit(get_model) for _ in range(8)]
                assert entered.wait(timeout=5)
                release.set()
                assert all(future.result(timeout=5) is model for future in futures)
            assert loader.call_count == 1
    finally:
        release.set()
        _load_model.cache_clear()


@pytest.mark.parametrize("selected", [0, 1, 2])
def test_selected_segment_confidence_does_not_average_other_phonemes(selected):
    logits = np.full((15, 5), -5.)
    logits[:, 0] = 5.
    for index in range(3):
        logits[index * 5 + 1:index * 5 + 4, :] = -5.
        logits[index * 5 + 1:index * 5 + 4, index + 1] = 5.
    good = align_posteriors(logits, [1, 2, 3], 0, .3, segment_scores=True)
    unrelated = (selected + 1) % 3
    weakened = logits.copy()
    weakened[unrelated * 5 + 1:unrelated * 5 + 4, unrelated + 1] = -3.
    changed = align_posteriors(weakened, [1, 2, 3], 0, .3, segment_scores=True)
    assert good[selected]["gop_score"] > 99
    assert changed[selected]["gop_score"] == pytest.approx(good[selected]["gop_score"])
    assert changed[unrelated]["gop_score"] < good[unrelated]["gop_score"]
    wrong = logits.copy()
    wrong[selected * 5 + 1:selected * 5 + 4, selected + 1] = -5.
    wrong[selected * 5 + 1:selected * 5 + 4, 4] = 5.
    substituted = align_posteriors(wrong, [1, 2, 3], 0, .3, segment_scores=True)
    assert substituted[selected]["gop_score"] < 1


def test_selected_segment_penalizes_competing_speech_around_a_single_target_spike():
    logits = np.full((24, 4), -5.)
    logits[:20, 3] = 5.
    logits[10, :] = [-5., 5., -5., -5.]
    logits[20:, 2] = 5.
    scores = align_posteriors(logits, [1, 2], 0, .5, segment_scores=True)
    assert scores[0]["gop_score"] < 1


def test_api_returns_selected_occurrence_not_word_mean():
    class SelectedModel:
        model_id = "selected-test"

        def score(self, samples, phonemes, target_phoneme_index):
            assert phonemes == ["m", "e", "ʒ", "ə"]
            assert target_phoneme_index == 2
            return [dict(phoneme=p, gop_score=81 if i == 2 else 12,
                         start_time=i * .05, end_time=(i + 1) * .05) for i, p in enumerate(phonemes)]

    with patch("app.main.get_model", return_value=SelectedModel()):
        response = client.post("/v1/score/phoneme",
            data={"target_transcript": "measure", "expected_ipa": "m e ʒ ə", "target_phoneme_index": "2"},
            files={"audio_file": ("test.wav", generate_test_wav(), "audio/wav")})
    assert response.status_code == 200
    body = response.json()
    assert body["overall_score"] == 81
    assert body["target_phoneme_index"] == 2
    assert body["scoring_method"] == "neural_ctc_phoneme"


@pytest.mark.parametrize("index", [-1, 3, 999])
def test_invalid_selected_index_rejected_before_model_loading(index):
    with patch("app.main.get_model") as loader:
        response = client.post("/v1/score/phoneme",
            data={"target_transcript": "ship", "expected_ipa": "ʃ ɪ p", "target_phoneme_index": str(index)},
            files={"audio_file": ("test.wav", generate_test_wav(), "audio/wav")})
    assert response.status_code in (400, 422)
    loader.assert_not_called()
