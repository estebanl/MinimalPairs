from unittest.mock import patch

import numpy as np
import pytest

from app.ctc_scorer import InvalidTarget, LocalPhonemeModel, RecordingQualityError, align_posteriors


VOCAB = {"<pad>": 0, "p": 1, "ɒ": 2, "ɔː": 3, "t": 4, "ɔ": 5, "oː": 6,
         "æ": 7, "əʊ": 8, "ɑː": 9, "o": 10}
GROUPS = ((2, 5), (3, 6))


def emissions(weights):
    probabilities = np.asarray(weights, dtype=float)
    probabilities /= probabilities.sum()
    logits = np.full((21, len(VOCAB)), -12.)
    logits[:, 0] = 8.
    logits[2, :] = -12.
    logits[2, 1] = 8.
    logits[10, :] = np.log(probabilities)
    logits[18, :] = -12.
    logits[18, 4] = 8.
    return logits, probabilities


def score(logits, reverse=False, groups=GROUPS):
    return align_posteriors(logits, [1, 3 if reverse else 2, 4], 0, .42,
                            segment_scores=True, selected_index=1,
                            alternative_target=2 if reverse else 3,
                            other_vowel_targets=(7, 8, 9, 10),
                            contrast_groups=groups[::-1] if reverse else groups)[1]


@pytest.mark.parametrize("winner,ending", [(2, "intended"), (5, "intended"), (3, "trap"), (6, "trap")])
def test_traditional_and_raised_realizations_keep_distinct_endings_and_shared_evidence(winner, ending):
    weights = np.full(len(VOCAB), .001)
    weights[winner] = .9
    logits, probabilities = emissions(weights)
    first, reverse = score(logits), score(logits, reverse=True)
    support = probabilities[list(GROUPS[0] + GROUPS[1])].sum()
    assert first["contrast_evidence"] == pytest.approx(support)
    assert first["contrast_evidence"] == reverse["contrast_evidence"]
    assert first["start_time"] == reverse["start_time"]
    assert first["end_time"] == reverse["end_time"]
    assert first["gop_score"] == pytest.approx(100 * probabilities[list(GROUPS[0])].sum() / support)
    intended, alternate = first["gop_score"], reverse["gop_score"]
    assert intended + alternate == pytest.approx(100.)
    actual = "intended" if intended >= 80 and intended - alternate >= 15 else "trap"
    assert max(intended, alternate) >= 80
    assert abs(intended - alternate) >= 15
    assert actual == ending


@pytest.mark.parametrize("other", [7, 8, 9, 10])
def test_third_vowels_still_block_a_confident_closed_pair_choice(other):
    weights = np.full(len(VOCAB), .001)
    weights[5], weights[other] = .3, .65
    logits, _ = emissions(weights)
    for reverse in (False, True):
        with pytest.raises(RecordingQualityError, match="different vowel dominates"):
            score(logits, reverse)


def test_expanded_pair_still_requires_absolute_and_relative_full_vocabulary_support():
    # A consonant, rather than a known competitor vowel, supplies the strongest evidence.
    for mass in (.01, .15):
        weights = np.full(len(VOCAB), .001)
        weights[1], weights[5] = .85, mass
        logits, _ = emissions(weights)
        for reverse in (False, True):
            with pytest.raises(RecordingQualityError, match="not supported reliably"):
                score(logits, reverse)


def test_a_raised_target_spike_cannot_hide_sustained_competing_speech():
    weights = np.full(len(VOCAB), .001)
    weights[5] = .9
    logits, _ = emissions(weights)
    unrelated = np.full(len(VOCAB), .001)
    unrelated[7] = .9
    for frame in range(6, 15):
        if frame != 10:
            logits[frame, :] = np.log(unrelated / unrelated.sum())
    with pytest.raises(RecordingQualityError):
        score(logits)


def test_mixed_traditional_and_raised_candidates_remain_uncertain():
    weights = np.full(len(VOCAB), .001)
    weights[2], weights[6] = .45, .45
    logits, _ = emissions(weights)
    assert score(logits)["gop_score"] == pytest.approx(50.)
    assert score(logits, reverse=True)["gop_score"] == pytest.approx(50.)


def test_a_supported_local_vowel_in_unrelated_speech_cannot_establish_the_prompted_phrase():
    weights = np.full(len(VOCAB), .001)
    weights[6] = .9
    logits, _ = emissions(weights)
    # The selected vowel is clear, but neither surrounding expected phone is said.
    logits[2, 1], logits[2, 7] = -12., 10.
    logits[18, 4], logits[18, 8] = -12., 10.
    for reverse in (False, True):
        with pytest.raises(RecordingQualityError, match="surrounding phrase"):
            score(logits, reverse)


def test_one_matching_context_phone_cannot_hide_an_unsupported_phrase():
    weights = np.full(len(VOCAB), .001)
    weights[5] = .9
    logits, _ = emissions(weights)
    logits[18, 4], logits[18, 7] = -12., 10.
    with pytest.raises(RecordingQualityError, match="surrounding phrase"):
        score(logits)


@pytest.mark.parametrize("groups", [((2, 5), (3, 5)), ((), (3, 6)), ((2, 0), (3, 6)),
                                   ((2, 5, 5), (3, 6)), ((2, 11), (3, 6)), ((5,), (3, 6))])
def test_ambiguous_or_invalid_pronunciation_groups_are_rejected(groups):
    logits, _ = emissions(np.ones(len(VOCAB)))
    with pytest.raises(InvalidTarget):
        score(logits, groups=groups)


def test_grouped_neighbors_cannot_bypass_the_shared_ctc_state_restriction():
    logits, _ = emissions(np.ones(len(VOCAB)))
    with pytest.raises(InvalidTarget, match="separated"):
        align_posteriors(logits, [5, 2, 4], 0, .42, segment_scores=True,
                         selected_index=1, alternative_target=3, contrast_groups=GROUPS)


def test_runtime_groups_only_the_declared_back_vowel_phrase_pair_and_preserves_public_labels():
    model = LocalPhonemeModel.__new__(LocalPhonemeModel)
    model.vocab, model.blank_id = VOCAB, 0
    weights = np.full(len(VOCAB), .001)
    weights[5] = .9
    logits, _ = emissions(weights)
    samples = np.zeros(6720, dtype=np.float32)
    with patch.object(model, "infer_logits", return_value=logits), \
            patch("app.ctc_scorer.prepare_word_audio", return_value=(samples, 0)):
        first = model.score(samples, ["p", "ɒ", "t"], 1,
                            utterance_kind="phrase", alternative_phoneme="ɔː")
        reverse = model.score(samples, ["p", "ɔː", "t"], 1,
                              utterance_kind="phrase", alternative_phoneme="ɒ")
        assert first[1]["phoneme"] == "ɒ"
        assert reverse[1]["phoneme"] == "ɔː"
        assert first[1]["gop_score"] > 99
        assert reverse[1]["gop_score"] < 1
        # The same narrow [ɔ] must not become a global alias for every /ɒ/ request.
        with pytest.raises(RecordingQualityError):
            model.score(samples, ["p", "æ", "t"], 1,
                        utterance_kind="phrase", alternative_phoneme="ɒ")
