"""Offline candidate evaluation only; not a calibrated pronunciation scorer."""

import numpy as np


def sequence_log_probability(logits, targets, blank_id):
    """Sum every CTC path collapsing to targets using the forward recurrence.

    Unlike forced-path posterior averaging, this retains blank emissions and
    marginalizes alignment uncertainty. A likelihood or pairwise preference is
    not a pronunciation grade; empty-sequence likelihood remains meaningful.
    """
    logits = np.asarray(logits, dtype=np.float64)
    if logits.ndim != 2 or not logits.size or not np.isfinite(logits).all():
        raise ValueError("Expected finite frame-by-vocabulary logits")
    frames, vocabulary = logits.shape
    if not 0 <= blank_id < vocabulary or any(t == blank_id or not 0 <= t < vocabulary for t in targets):
        raise ValueError("Invalid CTC token index")
    shifted = logits - logits.max(axis=1, keepdims=True)
    logp = shifted - np.log(np.exp(shifted).sum(axis=1, keepdims=True))
    if not targets:
        return float(logp[:, blank_id].sum())
    states = [blank_id]
    for target in targets:
        states.extend((target, blank_id))
    scores = np.full(len(states), -np.inf)
    scores[0] = logp[0, blank_id]
    scores[1] = logp[0, targets[0]]
    for time in range(1, frames):
        following = np.full(len(states), -np.inf)
        for state, token in enumerate(states):
            previous = [scores[state]]
            if state > 0:
                previous.append(scores[state - 1])
            if state > 1 and token != blank_id and token != states[state - 2]:
                previous.append(scores[state - 2])
            following[state] = np.logaddexp.reduce(previous) + logp[time, token]
        scores = following
    return float(np.logaddexp(scores[-1], scores[-2]))
