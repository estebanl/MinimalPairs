import numpy as np
import pytest
from unittest.mock import patch
from app.ctc_scorer import prepare_word_audio, RecordingQualityError, align_posteriors, LocalPhonemeModel
from test_scorer import client, generate_test_wav


def word():
    t = np.arange(6400) / 16000
    return (np.sin(2 * np.pi * 180 * t) * np.sin(np.pi * np.arange(6400) / 6400) ** 2 * .2).astype(np.float32)


def test_pause_gain_and_dc_do_not_change_normalized_word():
    original = np.pad(word(), (3200, 3200))
    base, offset = prepare_word_audio(original)
    transformed, shifted = prepare_word_audio(np.pad(original * .15, (48000, 96000)) + .07)
    assert shifted == offset + 48000
    np.testing.assert_allclose(base, transformed, atol=2e-6)


def test_weak_background_events_do_not_extend_the_word_to_the_recording_end():
    clean = np.pad(word(), (8000, 96000))
    noisy = clean.copy()
    for start in (40000, 60000, 80000):
        noisy[start:start + 1600] = word()[2000:3600] * .08
    base, offset = prepare_word_audio(clean)
    prepared, shifted = prepare_word_audio(noisy)
    assert offset == shifted
    np.testing.assert_array_equal(base, prepared)


@pytest.mark.parametrize('samples,code', [
    (np.zeros(16000), 'no_clear_speech'),
    (np.full(16000, .4), 'no_clear_speech'),
    (np.random.default_rng(1).normal(0, .02, 16000), 'noisy_audio'),
    (np.pad(np.ones(1600), (8000, 8000)), 'clipped_audio'),
    (np.pad(np.array([.2]), (8000, 8000)), 'no_clear_speech'),
    (np.concatenate([np.zeros(8000), word(), np.zeros(16000), word(), np.zeros(8000)]), 'multiple_utterances'),
])
def test_unreliable_input_is_not_a_low_pronunciation_grade(samples, code):
    with pytest.raises(RecordingQualityError) as error:
        prepare_word_audio(samples)
    assert error.value.code == code


def test_uncertain_emissions_are_rejected_but_clear_substitution_gets_low_score():
    logits = np.full((15, 5), -5.)
    logits[:, 0] = 5.
    logits[3, :] = [-5., -5., -5., 5., -5.]  # clear competing consonant
    logits[10, :] = [-5., -5., 5., -5., -5.]
    assert align_posteriors(logits, [1, 2], 0, .3, segment_scores=True, selected_index=0)[0]['gop_score'] < 1
    logits[3, :] = [0., -.2, -.2, .1, -.2]
    with pytest.raises(RecordingQualityError, match='reliably'):
        align_posteriors(logits, [1, 2], 0, .3, segment_scores=True, selected_index=0)


def test_api_explains_quality_failure_without_any_score():
    with patch('app.main.get_model') as model:
        model.return_value.score.side_effect = RecordingQualityError('noisy_audio', 'Try a quieter room.')
        response = client.post('/v1/score/phoneme', data={'target_transcript':'pear', 'expected_ipa':'p eə', 'target_phoneme_index':'0'}, files={'audio_file':('test.wav', generate_test_wav(), 'audio/wav')})
    assert response.status_code == 400
    assert response.json() == {'detail': {'code': 'noisy_audio', 'message': 'Try a quieter room.'}}


def test_model_returns_timestamps_in_original_microphone_recording():
    model = LocalPhonemeModel.__new__(LocalPhonemeModel)
    model.vocab = {'p': 1, 'eə': 2}
    model.blank_id = 0
    audio = np.pad(word(), (48000, 96000))
    prepared, offset = prepare_word_audio(audio)
    logits = np.full((30, 3), -5.)
    logits[:, 0] = 5.
    logits[8] = [-5., 5., -5.]
    logits[18] = [-5., -5., 5.]
    with patch.object(model, 'infer_logits', return_value=logits) as infer:
        result = model.score(audio, ['p', 'eə'], 0)
    np.testing.assert_array_equal(infer.call_args.args[0], prepared)
    assert result[0]['start_time'] == pytest.approx(offset / 16000 + 8 * len(prepared) / 16000 / 30)
    assert 2.8 < result[0]['start_time'] < result[-1]['end_time'] < 3.6


def test_upload_silence_does_not_dilute_an_audible_word_before_endpointing():
    import io
    import wave
    from app.main import decode_audio
    audio = np.pad(word() * .08, (16000 * 10, 16000 * 19))
    assert np.sqrt(np.mean(audio ** 2)) < .001
    buffer = io.BytesIO()
    with wave.open(buffer, 'wb') as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(16000)
        wav.writeframes((audio * 32768).astype('<i2').tobytes())
    decoded = decode_audio(buffer.getvalue())
    prepared, offset = prepare_word_audio(decoded)
    assert len(prepared) < 16000
    assert offset > 16000 * 9
