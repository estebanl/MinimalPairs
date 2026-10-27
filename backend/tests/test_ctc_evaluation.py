from itertools import product

import numpy as np
import pytest

from app.ctc_evaluation import sequence_log_probability


@pytest.mark.parametrize("target", [[], [1], [1, 1], [1, 2], [2, 1]])
def test_forward_probability_matches_exhaustive_path_sum(target):
    probabilities = np.array([[.5, .3, .2], [.2, .6, .2], [.3, .2, .5], [.4, .4, .2]])
    expected = 0.0
    for path in product(range(3), repeat=4):
        collapsed = [token for index, token in enumerate(path)
                     if token != 0 and (index == 0 or token != path[index - 1])]
        if collapsed == target:
            expected += np.prod([probabilities[time, token] for time, token in enumerate(path)])
    assert np.exp(sequence_log_probability(np.log(probabilities), target, 0)) == pytest.approx(expected)


def test_impossible_repeat_has_zero_probability():
    assert sequence_log_probability(np.zeros((2, 3)), [1, 1], 0) == -np.inf


@pytest.mark.parametrize("logits,target,blank", [(np.array([[np.nan, 0]]), [1], 0),
    (np.zeros((3, 2)), [0], 0), (np.zeros((3, 2)), [2], 0), (np.zeros((3, 2)), [1], 2)])
def test_invalid_inputs_fail_explicitly(logits, target, blank):
    with pytest.raises(ValueError):
        sequence_log_probability(logits, target, blank)
