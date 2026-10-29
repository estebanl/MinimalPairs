# Independent human evaluation of Stories

**Status: study pending.** No independent human dataset was available when this
protocol and evaluator were added. No human-speaker accuracy or calibration result
is claimed. Generated reference/control recordings remain integration checks.

## Collection and annotation protocol

1. Freeze the app/catalog revision, backend version, checkpoint identity, protocol
   version, and the current ending rules (80/100 with a 15-point lead). Predeclare
   the sample size and acceptable false-ending/retry rates before scoring. This
   repository does not establish product-release or calibration thresholds.
2. Recruit independent speakers across the learner accents and microphones you
   intend to support. Obtain explicit permission for evaluation and for uploading
   recordings to the chosen scoring service. Keep identifiable consent records
   outside the repository; use pseudonymous speaker IDs in the manifest.
3. For every catalog story, collect multiple speakers and takes of the full
   intended phrase and its alternate-word version, ambiguous vowels, and unrelated
   speech. Record real speech with the intended device microphone, including
   realistic room conditions and pauses. Never use speech synthesis, voice
   conversion, or the bundled references as human observations. Keep all takes,
   including unclear or rejected ones; do not select only successful recordings.
4. Have two independent reviewers, blinded to scorer output, label what is heard:
   `intended`, `trap`, `ambiguous`, or `unrelated`. A prompted word alone is not a
   ground-truth pronunciation label. Record reviewer IDs, disagreements and their
   resolution in the annotation record; retain unresolved cases as ambiguous.
   Document dialect mergers rather than forcing a distinction a speaker does not
   make. A story's expected transcript always stays fixed during scoring.
5. Store original recordings separately and produce intact 16 kHz, 16-bit, mono
   PCM WAV evaluation copies, 80 ms–30 seconds and at most 1 MB. Record conversion
   steps and collection conditions in dataset provenance. Avoid processing that
   changes the vowel or suppresses difficult examples. Dataset audio and consent
   files must not be committed or bundled in the app.
6. Separate speakers into development and held-out evaluation groups before
   adjusting rules. If thresholds change, record the reason and development
   evidence, add regression cases, then rerun an untouched held-out set. Keep the
   original manifest, audio hashes, raw responses and reports for comparison.

## Manifest and execution

Copy [the manifest template](../specs/stories/human_recordings.example.json) into
the private dataset folder. Replace every placeholder, add all recordings, and
set `consent_confirmed` to `true` only after confirming collection/upload consent.
`wav_path` is relative to the manifest. `annotation` references the blinded review
and resolution record. `provenance` identifies the collection, conversion, consent
and annotation procedures without exposing participant identities.

The evaluator rejects empty datasets, duplicate recording IDs, same-story WAV
paths or identical audio hashes, unknown stories/labels, missing metadata, non-human source declarations,
unconfirmed consent, bundled generated references, and invalid WAV formats. These
checks do not prove speech is natural or consent is authentic; a collection audit
is still required. Audio hashes expose repeated clips in the report. Reusing a
control across stories is allowed but does not create independent observations.

Start the configured backend using the existing scoring setup, then run:

```sh
PYTHONDONTWRITEBYTECODE=1 python3 scripts/evaluate_human_stories.py \
  /private/path/to/study/manifest.json \
  --endpoint http://127.0.0.1:8000 \
  --output build/reports/stories/human-study-v1.json
```

Remote endpoints require HTTPS. The optional service key is read from
`PHONEME_SCORER_FUNCTION_KEY`; `--function-key-env` selects another environment
variable. Keys are never included in the report or accepted in endpoint URLs.
Redirects are not followed. Running the command uploads each clip twice to the
specified endpoint, once per contrast direction; choose the approved service.

Exit status is `0` for a run with at least one valid comparison and without false
endings or infrastructure/contract failures, `1` otherwise, and `2` for invalid inputs or inability to write a
report. A zero status is **not** study completeness or pronunciation calibration.
Empty/template datasets produce no report. If all clips are quality-rejected, the
report says `no_valid_comparisons` and the command exits `1`; rejecting every
recording does not establish a successful evaluation. Reports include pseudonymous metadata
and raw service responses and should remain with the private evaluation artifacts.

## Interpreting a report

The evaluator reads the current runtime catalog and checks it against the story
specification. Both directions must return the app's phrase-contrast method,
valid aligned phonemes, the expected target index/alternate, matching model IDs,
and a shared target region/evidence. The selected vowel, never the phrase average,
chooses an ending using the unchanged 80/15 rules.

Each clip is classified separately:

- `evaluated`: both responses satisfy the contract; outcome is intended, trap, or
  uncertain. A confident outcome for ambiguous/unrelated speech is a false ending.
- `quality_rejected`: the service explicitly rejects audio/evidence and the app
  would request a retry. It is kept separate from a valid uncertain score.
- `service_error`: transport failure or an unexpected HTTP response. It never
  counts as a successful rejection of unrelated speech or as a scored retry.
- `contract_error`: a successful response violates the app's comparison contract.
  It never establishes an ending or reliable scoring coverage.

Overall and per-story, speaker, accent and microphone summaries include counts,
expected-label coverage, and these denominators:

| Metric | Definition |
| --- | --- |
| False-ending rate per evaluated clip | Wrong confident endings / clips with two valid responses |
| False-ending rate per confident ending | Wrong confident endings / all confident endings |
| Retry rate | (Valid uncertain scores + quality rejections) / (evaluated clips + quality rejections) |

Unavailable rates are `null`, not zero. Service/contract failures are reported
separately and excluded from retry denominators. Missing expected labels by story
describe **collected** coverage, not successful scoring coverage. Inspect group
counts and failure counts before interpreting a rate; report all missing stories,
labels, accents and microphone conditions. Do not pool different checkpoints to
claim a single model's reliability; `model_ids` identifies mixed-model runs.

Publish a deidentified study report with recruitment/sample counts, coverage,
false-ending and retry rates by group, uncertainty from the finite sample,
annotation agreement, rejected cases, infrastructure failures, and limitations.
The JSON summaries alone do not supply confidence intervals or human rating-scale
calibration. Scores remain **experimental contrast confidence, not pronunciation
accuracy**, unless a separate expert-supported calibration study justifies a
stronger claim.

## Tool regression checks

```sh
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s scripts/tests -p 'test_evaluate_human_stories.py'
```

These tests use explicit fake responses and temporary WAV fixtures. They verify
the evaluator and do not constitute independent human-speaker validation.
