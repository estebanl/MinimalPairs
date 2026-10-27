# Articulator Lab scoring

The app continues to work offline. Its default score is an **acoustic estimate**
from LPC formants and vowel duration. It does not recognize the example word.
Practise the vowel on its own; the word identifies the target sound. The UI names
the scoring method and explicitly reports fallback when a configured model
service cannot return a valid result.

## Optional neural service

The backend now runs actual phoneme CTC inference using a locally provisioned
Hugging Face Wav2Vec2 checkpoint. It aligns the supplied IPA sequence with a
Viterbi CTC path, including blank transitions between repeated phonemes.
Segment times come from CTC token-emission frames, not equal-duration slicing.
CTC often emits a phoneme in only one frame: these intervals are not validated
phonetic segment boundaries and must not be used to measure vowel duration or
automatically crop a complete vowel.

The returned legacy `gop_score` field is **uncalibrated model confidence**:
100 × geometric mean of the aligned token posterior × sequence agreement.
Sequence agreement penalizes the forced path relative to greedy model emissions
on nonblank-predicted frames. This prevents one matching frame from hiding a
sustained competing phoneme. Blank/silent frames do not dilute that penalty.
The overall score is the mean of phoneme scores. Neither this score nor the
existing acoustic estimate has been calibrated against expert ratings; they
must not be compared as interchangeable assessment scales. MFA is not used.

`expected_ipa` is a whitespace-separated sequence of model phoneme tokens, not
an orthographic transcript. The current app sends one vowel. The provisioned model now rejects that task
explicitly, so the app uses its labeled acoustic fallback. The experimental API
can still align multi-phoneme IPA sequences, which callers must supply themselves. Broad
British DRESS `/e/` maps to the model's `[ɛ]` token while preserving the public
`e` label. Unsupported phonemes are rejected, never silently converted to an
unknown token. `target_transcript` is descriptive metadata, not automatic
phonemization.

### Provisioning

Use Python 3.11–3.13 and install `backend/requirements-model.txt` into a virtual
environment. Base `requirements.txt` still supports API/alignment tests without
PyTorch or model weights. The optional direct dependencies are pinned to
PyTorch 2.9.1 and Transformers 4.57.3.

Compatible source: [facebook/wav2vec2-xlsr-53-espeak-cv-ft](https://huggingface.co/facebook/wav2vec2-xlsr-53-espeak-cv-ft),
Apache-2.0 per its model card. Pin a reviewed revision when provisioning;
`2c733782da5604684829819a5eb744c193fe9398` is a published model revision.
Download its configuration, feature extractor configuration, `vocab.json`, and
weights into a local directory using your deployment tooling. Preserve its
license and model card. The server does **not** download anything during requests,
does not execute model-provided Python code, and uses `local_files_only=True`.

For example, after installing the optional requirements, provision explicitly:

```python
from huggingface_hub import snapshot_download
snapshot_download(
    repo_id="facebook/wav2vec2-xlsr-53-espeak-cv-ft",
    revision="2c733782da5604684829819a5eb744c193fe9398",
    local_dir="./models/phoneme-ctc",
    allow_patterns=["*.json", "*.bin", "*.safetensors", "README.md", "LICENSE*"],
)
```

Then run from `backend/`:

```sh
PHONEME_MODEL_DIR=/absolute/path/to/models/phoneme-ctc \
PHONEME_MODEL_ID=facebook/wav2vec2-xlsr-53-espeak-cv-ft@2c733782 \
python -m uvicorn app.main:app
```

The model loads lazily and serializes inference per process. Allow enough memory
for the large checkpoint; additional server workers each load their own copy.
`GET /health` reports process liveness separately from `model_status`:
`unavailable` (no local directory), `not_loaded` (directory exists, not verified),
or `loaded` (successfully loaded). A missing dependency/checkpoint or failed
inference returns 503, never an invented score. Restart after changing model
configuration. Input is bounded to 1 MB, 80 ms–30 s, 16 kHz, 16-bit mono PCM WAV;
invalid/silent/truncated data is rejected before model inference.

### App connection

Set `PhonemeScorerConfiguration.baseUrl` before constructing the app root on any
platform. Desktop also reads the `minimalpairs.scorerUrl` JVM property or
`MINIMAL_PAIRS_SCORER_URL` environment variable. iOS reads that environment
variable or a `PhonemeScorerURL` Info.plist entry. Android hosts use the shared
configuration property. Use an HTTPS endpoint reachable by the device; a mobile
device's localhost is the device itself. No production endpoint is preconfigured.

Configuring an endpoint sends recorded audio to it for evaluation. Camera frames
remain local. The backend neither stores audio nor includes audio in responses.
The current app retains only its existing numerical score/target history; model
provenance is displayed for the current attempt, not persisted in historical rows.

The client validates target tokens, timing order, finite scores, model provenance,
and recording bounds. The client times out model requests after 30 seconds and
falls back explicitly; cancellation remains cancellation. Snake-case API fields
are mapped with `SerialName` rather than accidentally failing deserialization.

## Validation

`python -m pytest tests` (from `backend/`) exercises CTC alignment, repeated
phonemes, wrong-target confidence, a target spike amid a competing phoneme,
malformed emissions, WAV validation, and unavailable/inference error paths.
API tests inject deterministic model emissions; they do not claim pretrained
model accuracy. `ScorerTransportTest` exercises actual JSON transport decoding,
model provenance, malformed result fallback, and cancellation.

### Original checkpoint smoke run, before the isolated-task guard (2026-10-27)

The pinned checkpoint was downloaded explicitly and loaded successfully using
Python 3.13.18, PyTorch 2.9.1, and Transformers 4.57.3 on this Mac. The isolated
environment is `build/phoneme-model-venv`; weights and their SHA-256 manifest are
in `build/models/phoneme-ctc`. Both are ignored build artifacts. Reproduce from
the repository root:

```sh
python3.13 -m venv build/phoneme-model-venv
build/phoneme-model-venv/bin/python -m pip install -r backend/requirements-model.txt
HF_HOME=build/huggingface-cache build/phoneme-model-venv/bin/python scripts/provision_phoneme_model.py
HF_HOME=build/huggingface-cache build/phoneme-model-venv/bin/python scripts/validate_phoneme_model.py
```

The provisioning script downloads only the pinned files and records checksums.
The validation script runs offline through the actual FastAPI endpoint with the
real checkpoint and writes `build/reports/phoneme-model/smoke.json`. It exercised
34 requests: 24 scored and 10 rejected. This original evidence is preserved in
`build/reports/phoneme-model/smoke-before-isolated-guard.json`. All ten bundled word references produced
results for both candidate IPA sequences; expected words ranked above their
contrast alternatives for eight of ten examples. `bad` and `cot` did not. This
small reference set is not an accuracy benchmark or expert-labeled learner data.

The app's isolated-vowel request shape was checked separately. Fixed crops of
`sheep.wav` and `ship.wav` from 0.18–0.38 seconds produced scores, but the ship
crop incorrectly preferred /iː/ (0.312) over /ɪ/ (0.172). The sheep crop preferred
/iː/ (24.24) over /ɪ/ (0.279). These are exact time windows, not independently
verified vowel boundaries. Shorter 0.20–0.35-second crops and generated FLEECE/KIT
vowels were rejected because the model detected no phoneme. Silence and seeded
noise also returned 400. The script retains these failures rather than asserting
a pronunciation-quality pass threshold.

First observed loading took about 10.9 seconds; warmed file-cache runs took
about 1.5–2.6 seconds, with individual scored requests around 0.06–0.13 seconds on
this host. These are observations, not cross-device latency guarantees. Concurrent
first requests share one allocation, covered by a regression test.

**Deployment conclusion:** inference and API integration work, but this model
has not demonstrated reliable isolated-vowel coaching. It remains experimental,
explicitly labeled, and opt-in with no default app endpoint. Provisioning does
not establish human calibration. Before enabling it for learners, evaluate
recorded isolated vowels, noise and microphone variations against expert
judgments, choose or train a suitable model, and calibrate the scoring scale.
See the [Transformers phoneme model documentation](https://huggingface.co/docs/transformers/model_doc/wav2vec2_phoneme)
for the upstream model interface.

### Independent annotated-nucleus evaluation and task guard

The next check uses the Hillenbrand et al. (1995) corpus, distributed by
[Santiago Barreda with the original author's permission](https://github.com/santiagobarreda/hillenbrand_et_al_1995).
The pinned mirror revision is `6dd44decc8ef5b537cbb54732a3b00c8fa652e65`;
the 21,748,822-byte archive has SHA-256
`2560548591e3a726c88549b6dc9d226995616c26874d601c69fee8b9eee9d730`.
The mirror's README/license are retained with the ignored local archive; corpus
audio is not bundled into the app or repository.

Selection was fixed to **all 372 adult FLEECE/KIT and TRAP/DRESS recordings from
93 speakers**. Crops use the published vowel-nucleus start/end annotations, not
model emission spans or hand-selected favorable windows. No weights, scoring
parameters, or thresholds were trained on this corpus. It is American English
in /hVd/ context, not British learner speech held in isolation; independence
from the checkpoint's pretraining data is not established.

Alongside the existing forced-path scorer, the evaluator checks a standard CTC
forward recurrence summing all paths for each candidate sequence. The recurrence
is verified against exhaustive path enumeration in unit tests. It is evaluation
code only; it does not replace runtime scoring. Alignment marginalization is a
reasonable research candidate given the documented limitations of peaky CTC
outputs ([Cao et al., 2024](https://www.isca-archive.org/interspeech_2024/cao24b_interspeech.html),
[Li et al., 2026](https://aclanthology.org/2026.bea-1.3/)); it cannot repair an
unsuitable acoustic model by itself.

Observed results on the pinned checkpoint:

| Nucleus label | Recordings | Existing scorer accepted | Existing scorer ranked correctly | All-path rank correct, ignoring rejection |
| --- | ---: | ---: | ---: | ---: |
| FLEECE /iː/ | 93 | 0 | 0 | 93 |
| KIT /ɪ/ | 93 | 1 | 1 | 14 |
| TRAP /æ/ | 93 | 1 | 0 | 18 |
| DRESS /ɛ/ | 93 | 0 | 0 | 35 |
| Total | 372 | 2 | 1 | 160 |

The 370 rejections reported no detected phoneme (greedy blank emissions). The
all-path candidate ranked only 160/372 (43.0%) correctly even when forced to
choose, and retains just one correct result with the same rejection policy.
It was **not promoted to runtime**. Crops were 125–486 ms with RMS 0.178–0.497
of full scale, so these were not silent inputs.

Prespecified full-word controls used adult speakers `m01` and `w01` for all four
vowels (eight /hVd/ recordings). All eight produced scores; seven preferred the
expected vowel. This shows the loader can process speech from this corpus, but
does not establish general word-level accuracy or attribute every error solely
to isolation. Per-file duration, RMS, peak, candidate values and rejection reasons
are retained in `build/reports/phoneme-model/isolated-corpus.json`.

Reproduce without changing the runtime model or endpoint:

```sh
build/phoneme-model-venv/bin/python scripts/evaluate_isolated_vowels.py --download
```

The explicit download provisions the 22 MB pinned archive and source notices.
Subsequent runs can omit `--download` and remain offline. The evaluator bypasses
the service task guard to inspect the original model emissions; it does not
bypass it in application requests.

**Runtime consequence:** the API now rejects single-phoneme requests with an
explicit unsupported-task error before model loading; `LocalPhonemeModel.score`
also guards direct calls before inference. The app's
existing labeled acoustic fallback handles this response. Experimental
multi-phoneme requests remain available. This closes an unsafe grading path; it
does **not** close the reliable neural isolated-vowel scoring requirement.
The guarded real-API smoke run completed 34 requests: 20 multi-phoneme scores
and 14 expected rejections. Evidence is in
`build/reports/phoneme-model/smoke-isolated-guard.json`, separate from the original
pre-guard results. Before replacing the guard, a suitable isolated-vowel model
needs independently held-out speaker recordings, relevant dialect/learner
coverage, explicit invalid-input controls, and expert-supported calibration.
