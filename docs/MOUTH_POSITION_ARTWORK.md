# Mouth-position reference artwork

The Mouth position tab shows a static front view and left-facing side cutaway for
the selected vowel. The Sound map retains live acoustic feedback. Recording does
not change the reference pose. These are simplified teaching illustrations,
not measurements of a learner's anatomy.

## Resources and maintenance

Artwork is bundled under `composeApp/src/commonMain/composeResources/drawable/`.
Each `mouth_<lexical-set>_front.jpg` has a matching `_side.jpg`; both use a 3:2
canvas and an opaque white illustration backdrop. The UI supplies all labels,
placement cues, and accessibility descriptions outside the images. Images fit
their bounds without cropping and retain their original colors in dark mode.

`MouthPositionReferences` maps the ten phonemes to resources and descriptions.
Shared vowels reuse the same entry across contrasts. Keep these poses separate
from the older contrast-level `VisualIndicators` used elsewhere in the app.
For a new vowel, add both resources and a catalog entry; the coverage test will
fail if a contrast introduces an unsupported sound. The UI also has a text-only
fallback rather than displaying an unrelated illustration.

## Phonetic basis

The inventory follows the app's British-English word pairs and phonemic labels.
The [International Phonetic Association vowel chart](https://www.internationalphoneticassociation.org/content/ipa-chart)
provides the height, backness, and rounding framework. The
[BBC Sounds of English symbol sheet](https://downloads.bbc.co.uk/learningenglish/features/themed_weeks/day_2_5_days_to_improve_your_pronunciation.pdf)
confirms the British-English labels. The
[University of Sheffield study of British-English vowel variation](https://etheses.whiterose.ac.uk/3716/1/D_Williams_PhD_thesis.pdf)
provides context for DRESS and variation between speakers.

| Asset key | Phoneme | Intended contrast in the illustration |
| --- | --- | --- |
| fleece | iː | High front tongue; narrow, spread, unrounded lips |
| kit | ɪ | Slightly lower and less front than FLEECE; relaxed lips |
| dress | e | Open-mid front; unrounded, medium opening |
| trap | æ | Low front; unrounded, wider opening |
| palm | ɑː | Low back; unrounded, wide opening |
| strut | ʌ | Relaxed low-central teaching pose; unrounded |
| foot | ʊ | Near-high, toward the back; loosely rounded |
| goose | uː | High back teaching pose; small, rounded opening |
| lot | ɒ | Low back; rounded, large opening |
| thought | ɔː | Open-mid back; rounded, smaller opening than LOT |

The app's /e/ represents DRESS (approximately [ɛ]), not cardinal [e]. STRUT is
shown centrally rather than as a strongly retracted cardinal [ʌ]. GOOSE uses
the traditional back-vowel reference consistent with the app's existing catalog;
actual British-English realizations can be more central. No artwork encodes an
exact jaw distance, tongue coordinate, or acoustic target.

## Generation and review

Generated with the built-in imagegen tool. The exact prompt set is recorded in
`design/mouth-position-prompts.json`. FLEECE supplies the visual templates for
the remaining poses. The original generated images remain outside the project;
the app consumes only the bundled copies. The final PNGs are encoded as JPEGs
at quality 90 for packaging, retaining the original 1536 × 1024 dimensions.

Review the visible lip shape, jaw opening, tongue height/backness, continuous
tongue body, and clear airway together with the placement cue. Images are
illustrative and have not been independently reviewed by a phonetics specialist.
Do not describe them as clinically or anatomically certified.

## Verification

- Common tests cover all current phonemes, distinct views, reuse across pairs,
  and unsupported-sound handling.
- JVM resource tests decode all 20 bundled images without network access and
  check image dimensions and consistent aspect ratios.
- For UI review, switch both sounds in all six contrasts, record with the Mouth
  position tab selected, and check the Sound map still receives live feedback.
- JVM Compose scene tests exercise both sounds in all six contrasts, recording
  stability, and returning to the Sound map. They render narrow and wide cards
  in both themes with normal and enlarged text, checking labels for overflow.
  Review images are written to `composeApp/build/reports/mouth-position/`.
