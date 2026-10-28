from unittest.mock import patch

import numpy as np
import pytest

from app.ctc_scorer import LocalPhonemeModel, RecordingQualityError, InvalidTarget, prepare_word_audio, align_posteriors
from test_microphone_audio import word
from test_scorer import client, generate_test_wav


def post_phrase(**overrides):
    data = {
        "target_transcript": "Can I have that sheep, please?",
        "expected_ipa": "k ə n aɪ h æ v ð æ t ʃ iː p p l iː z",
        "target_phoneme_index": "11",
        "utterance_kind": "phrase",
    }
    data.update(overrides)
    return client.post("/v1/score/phoneme", data=data,
                       files={"audio_file": ("phrase.wav", generate_test_wav(), "audio/wav")})


def test_phrase_endpoint_selects_the_vowel_score_and_passes_phrase_mode():
    class Model:
        model_id = "phrase-test-model"

        def score(self, samples, tokens, index, *, utterance_kind):
            assert utterance_kind == "phrase"
            assert index == 11
            assert tokens[index] == "iː"
            return [dict(phoneme=token, gop_score=82. if i == index else 10.,
                         start_time=i * .01, end_time=(i + 1) * .01)
                    for i, token in enumerate(tokens)]

    with patch("app.main.get_model", return_value=Model()):
        response = post_phrase()
    assert response.status_code == 200
    body = response.json()
    assert body["scoring_method"] == "neural_ctc_phrase_phoneme"
    assert body["target_phoneme_index"] == 11
    assert body["overall_score"] == body["phonemes"][11]["gop_score"] == 82.
    assert "phrase" in body["feedback"]


@pytest.mark.parametrize("data,status", [
    ({"target_phoneme_index": None}, 400),
    ({"target_phoneme_index": "99"}, 400),
    ({"utterance_kind": "unknown"}, 422),
])
def test_invalid_phrase_selection_is_rejected_before_loading_the_model(data, status):
    with patch("app.main.get_model") as model:
        response = post_phrase(**data)
    assert response.status_code == status
    model.assert_not_called()


def test_phrase_quality_errors_remain_retryable_without_a_score():
    with patch("app.main.get_model") as model:
        model.return_value.score.side_effect = RecordingQualityError("uncertain_alignment", "Record the phrase again.")
        response = post_phrase()
    assert response.status_code == 400
    assert response.json()["detail"]["code"] == "uncertain_alignment"
    assert "overall_score" not in response.json()


def test_phrase_endpointing_preserves_internal_pauses_and_more_than_three_seconds():
    audio = np.concatenate([np.zeros(8000), word(), np.zeros(16000), word(),
                            np.zeros(16000), word(), np.zeros(8000)])
    with pytest.raises(RecordingQualityError, match="once"):
        prepare_word_audio(audio)
    prepared, offset = prepare_word_audio(audio, allow_phrase=True)
    assert len(prepared) > 48000
    assert 0 < offset < 8000
    model = LocalPhonemeModel.__new__(LocalPhonemeModel)
    model.vocab = {"h": 1, "iː": 2}
    model.blank_id = 0
    logits = np.full((30, 3), -5.)
    logits[:, 0] = 5.
    logits[8] = [-5., 5., -5.]
    logits[18] = [-5., -5., 5.]
    with patch.object(model, "infer_logits", return_value=logits) as infer:
        result = model.score(audio, ["h", "iː"], 1, utterance_kind="phrase")
    np.testing.assert_array_equal(infer.call_args.args[0], prepared)
    assert result[1]["gop_score"] > 99.
    assert result[0]["start_time"] == pytest.approx(offset / 16000 + 8 * len(prepared) / 16000 / 30)


@pytest.mark.parametrize("samples,code", [
    (np.zeros(16000), "no_clear_speech"),
    (np.random.default_rng(1).normal(0, .02, 16000), "noisy_audio"),
    (np.pad(np.ones(1600), (8000, 8000)), "clipped_audio"),
])
def test_phrase_mode_keeps_recording_quality_checks(samples, code):
    with pytest.raises(RecordingQualityError) as error:
        prepare_word_audio(samples, allow_phrase=True)
    assert error.value.code == code


def test_continuous_clean_phrase_does_not_require_twenty_percent_silence():
    audio = np.tile(word(), 8)
    with pytest.raises(RecordingQualityError, match="noise"):
        prepare_word_audio(audio)
    prepared, _ = prepare_word_audio(audio, allow_phrase=True)
    assert len(prepared) > 48000


def contrast_logits(probabilities):
    """Sparse CTC vowel evidence surrounded by clearly aligned consonants."""
    logits = np.full((21, len(probabilities)), -12.)
    logits[:, 0] = 8.
    logits[2, :] = -12.
    logits[2, 1] = 8.
    logits[10, :] = np.log(probabilities)
    logits[18, :] = -12.
    logits[18, 4] = 8.
    return logits


@pytest.mark.parametrize("probabilities", [
    [.02, .0001, .38, .007, .0001, .13, .123, .131, .2088],
    [.01, .0001, .128, .008, .0001, .22, .194, .15, .1398, .15],
])
def test_supported_multilingual_vowel_spread_uses_pair_confidence(probabilities):
    probabilities = np.asarray(probabilities)
    logits = contrast_logits(probabilities)
    first = align_posteriors(logits, [1, 2, 4], 0, .42, segment_scores=True,
                             selected_index=1, alternative_target=3)[1]
    reverse = align_posteriors(logits, [1, 3, 4], 0, .42, segment_scores=True,
                               selected_index=1, alternative_target=2)[1]
    assert first["gop_score"] == pytest.approx(100 * probabilities[2] / (probabilities[2] + probabilities[3]))
    assert first["gop_score"] >= 70
    assert reverse["gop_score"] < 15
    assert first["contrast_evidence"] == pytest.approx(probabilities[2] + probabilities[3])
    assert first["start_time"] == reverse["start_time"]
    assert first["end_time"] == reverse["end_time"]
    assert first["contrast_evidence"] == reverse["contrast_evidence"]


def test_clear_alternate_and_mixed_vowels_are_not_forced_to_the_intended_ending():
    probabilities = [.01, .001, .02, .95, .001, .008, .01]
    logits = contrast_logits(probabilities)
    intended = align_posteriors(logits, [1, 2, 4], 0, .42, segment_scores=True,
                                selected_index=1, alternative_target=3)[1]
    alternate = align_posteriors(logits, [1, 3, 4], 0, .42, segment_scores=True,
                                 selected_index=1, alternative_target=2)[1]
    assert alternate["gop_score"] > 95
    assert intended["gop_score"] < 5
    logits[9, :] = np.log([.01, .001, .95, .02, .001, .008, .01])
    for tokens, other in (([1, 2, 4], 3), ([1, 3, 4], 2)):
        score = align_posteriors(logits, tokens, 0, .42, segment_scores=True,
                                 selected_index=1, alternative_target=other)[1]["gop_score"]
        assert score < 70


def test_third_vowels_and_a_target_spike_in_unrelated_speech_remain_uncertain():
    logits = contrast_logits([.01, .001, .02, .01, .001, .95, .008])
    with pytest.raises(RecordingQualityError, match="not supported reliably"):
        align_posteriors(logits, [1, 2, 4], 0, .42, segment_scores=True,
                         selected_index=1, alternative_target=3)
    logits[6:15, :] = np.log([.01, .001, .02, .01, .001, .95, .008])
    logits[10, :] = np.log([.01, .001, .95, .02, .001, .008, .01])
    with pytest.raises(RecordingQualityError, match="not supported reliably"):
        align_posteriors(logits, [1, 2, 4], 0, .42, segment_scores=True,
                         selected_index=1, alternative_target=3)


def test_another_supported_vowel_cannot_win_by_pair_normalization():
    # /ɪ/ dominates but /e/ still has enough mass to win a closed /e/ vs /æ/ choice.
    logits = contrast_logits([.01, .001, .121, .027, .001, .256, .184, .141, .065, .194])
    with pytest.raises(RecordingQualityError, match="different vowel dominates"):
        align_posteriors(logits, [1, 2, 4], 0, .42, segment_scores=True,
                         selected_index=1, alternative_target=3, other_vowel_targets=(5,))


def test_contrast_api_returns_the_comparison_and_its_evidence():
    class Model:
        model_id = "contrast-test-model"

        def score(self, samples, tokens, index, *, utterance_kind, alternative_phoneme):
            assert utterance_kind == "phrase"
            assert alternative_phoneme == "ɪ"
            return [dict(phoneme=token, gop_score=98. if i == index else 10.,
                         contrast_evidence=.38 if i == index else None,
                         start_time=i * .01, end_time=(i + 1) * .01)
                    for i, token in enumerate(tokens)]

    with patch("app.main.get_model", return_value=Model()):
        response = post_phrase(alternative_phoneme="ɪ")
    assert response.status_code == 200
    body = response.json()
    assert body["scoring_method"] == "neural_ctc_phrase_contrast"
    assert body["overall_score"] == 98.
    assert body["alternative_phoneme"] == "ɪ"
    assert body["contrast_evidence"] == .38


@pytest.mark.parametrize("options", [
    {"alternative_phoneme": "iː"},
    {"alternative_phoneme": "ɪ ʊ"},
    {"alternative_phoneme": "ɪ", "utterance_kind": "word"},
    {"alternative_phoneme": "ɪ", "target_phoneme_index": None},
])
def test_invalid_contrast_requests_are_rejected_before_inference(options):
    with patch("app.main.get_model") as model:
        assert post_phrase(**options).status_code == 400
    model.assert_not_called()


def test_unsupported_or_identical_model_alternatives_are_rejected():
    model = LocalPhonemeModel.__new__(LocalPhonemeModel)
    model.vocab = {"h": 1, "ɛ": 2, "t": 4}
    model.blank_id = 0
    with patch.object(model, "infer_logits") as infer:
        for alternate in ("e", "unsupported", "<pad>"):
            with pytest.raises(InvalidTarget):
                model.score(np.zeros(4000), ["h", "e", "t"], 1,
                            utterance_kind="phrase", alternative_phoneme=alternate)
        infer.assert_not_called()
