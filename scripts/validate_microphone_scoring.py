"""Local-model pause/gain/noise regressions; not human pronunciation calibration."""
import json
import sys
import wave
from pathlib import Path
import numpy as np

root = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(root / 'backend'))
from app.ctc_scorer import LocalPhonemeModel, RecordingQualityError

model = LocalPhonemeModel(root / 'build/models/phoneme-ctc')
results = []
for word, tokens, index in [('pear', ['p', 'eə'], 0), ('bear', ['p', 'eə'], 0),
                            ('measure', ['m', 'e', 'ʒ', 'ə'], 2), ('rope', ['r', 'əʊ', 'p'], 2)]:
    with wave.open(str(root / f'composeApp/src/commonMain/composeResources/files/audio/words/{word}.wav')) as wav:
        audio = np.frombuffer(wav.readframes(wav.getnframes()), dtype='<i2').astype(np.float32) / 32768
    padded = np.pad(audio, (48000, 96000))
    reference = None
    for condition, samples in [('original', audio), ('pauses', padded), ('quiet', padded * .1),
                               ('dc_offset', padded * .1 + .03),
                               ('noise', padded * .1 + np.random.default_rng(42).normal(0, .003, len(padded)))]:
        try:
            score = model.score(samples.astype(np.float32), tokens, index)[index]['gop_score']
            result = dict(word=word, condition=condition, score=score)
            assert condition != 'noise', 'Noise-corrupted sample must request a retry, not produce a grade'
            if reference is None:
                reference = score
            assert abs(score - reference) < 5, result
            if word == 'bear':
                assert score < 10, result
            else:
                assert score > 70, result
        except RecordingQualityError as error:
            result = dict(word=word, condition=condition, rejected=error.code)
            assert condition == 'noise', result
        results.append(result)
        print(result, flush=True)
output = root / 'build/reports/phoneme-model/microphone-robustness.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(results, indent=2))
