# Selected-consonant scoring

Speak records the whole example word as context, but evaluates only the selected
consonant occurrence. It no longer computes whole-word reference similarity.
All consonant examples have a full broad British-English transcript in
`ConsonantScoringTargets`, including explicit initial, medial and final target
positions. Ambiguous or mismatched targets are rejected. The model maps public
/e/, /r/, and /ɪə/ to [ɛ], [ɹ], and [iə] respectively while retaining public labels.

## Request and score

`POST /v1/score/phoneme` receives the full, space-separated `expected_ipa`, WAV
recording and zero-based `target_phoneme_index`. The index must be within the
multi-phoneme transcript. Isolated-phoneme tasks remain unsupported.

CTC forced alignment locates each phoneme's emission frames. For selected-phoneme
scoring, inter-token blank gaps are divided halfway between adjacent emission
spans. The selected score is 100 times the geometric mean of the selected token's
posterior on its aligned frames plus greedy nonblank frames in that local region.
This includes local substitutions that alignment might otherwise push into blank
states. Silence does not dilute this penalty. Confidence for other segments is
not averaged or multiplied into the selected score.

Before inference, selected-phoneme requests use DC-independent 20ms energy
windows at 10ms hops to locate one word and supply a fixed 150ms context on both
ends (padding if necessary). The cropped waveform is centered and RMS normalized.
Returned emission timestamps include the crop offset and remain inside the
original recording; replay and saved duration retain the original audio.

Conservative quality gates reject peak window RMS below .003, more than 1%
full-scale clipping, peak-to-20th-percentile energy below 20, fewer than six
active windows, sustained activity cores separated by over 650ms, or word
activity spanning over three seconds. Cores require six windows above 20% of
peak energy (joining gaps up to 50ms); boundaries expand at 6% of peak or three
times background energy, joining gaps up to 100ms. Weaker distant events do not
extend the word. This is energy-based endpointing, not semantic verification of
the word count; sufficiently quiet extra speech can be missed.
These are engineering safeguards, not validated SNR or speech-detection metrics.
The selected region also needs a greedy nonblank emission with posterior at least
.6; otherwise alignment is uncertain. Low target posterior with a confident
competing sound can still produce a low score. No score floor or boost is applied.

Quality failures return HTTP 400 with `detail.code` and `detail.message`. Speak
maps known codes to recording guidance, retains replay, clears stale scores, and
saves nothing to Progress. Unknown failures retain the generic service error.

The result has method `neural_ctc_phoneme`, echoes the selected index, includes
ordered emission spans, and sets `overall_score` to that selected entry's score.
The client validates the method, model ID, transcript, index, finite scores,
ordered in-bounds spans, and equality of the selected and overall scores.

These are experimental model confidence scores, not calibrated pronunciation
accuracy. CTC emission intervals are not validated anatomical/phonetic boundaries.
Forced alignment can still mislocate a sound when pronunciation differs strongly
from the expected word. A successful request does not validate coaching accuracy.

## Availability and persistence

Consonant scoring requires a configured, reachable phoneme service with a locally
provisioned model. There is no word-similarity, fixed-score, or vowel-formant
fallback. Silence, invalid input, service failure, and malformed responses show a
recoverable error without saving a score. Audio remains available for replay.

The iOS host accepts a runtime HTTPS endpoint and optional access key during
provisioning, stores that connection in device-only Keychain, and restores it
before constructing the app root on subsequent launches. Explicit endpoint
changes replace the connection without reusing another endpoint's saved key.
Keychain failures never expose credentials in diagnostics; a failed save leaves
the current launch usable but does not promise access after restart. Credentials
are not included in app resources or distributed binaries.

Valid results save the target, duration, score, and scoring method. Database v4
adds `scoringMethod`; earlier rows and old imports default to `unknown` instead
of being relabeled as phoneme scores. Summaries group by method as well as target,
so legacy similarity results never become trends in phoneme confidence.

Run JVM tests, `python -m pytest tests` from backend, and optionally
`build/phoneme-model-venv/bin/python scripts/validate_consonant_scoring.py` with the
provisioned local model. The smoke script compares bundled target and opposite
word recordings; it is integration evidence, not held-out human validation.
