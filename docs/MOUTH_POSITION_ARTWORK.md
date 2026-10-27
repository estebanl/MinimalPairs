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

## Interactive 3D reference

The **3D model** tab loads actual VocalTractLab JD3 speaker meshes. The imported
SAMPA presets i, I, E, U, u, and O provide references for /iː/, /ɪ/, /e/ (DRESS),
/ʊ/, /uː/, and /ɔː/. Four additional **Derived teaching poses** cover /æ/, /ɑː/,
/ʌ/, and /ɒ/. They are explicitly labeled as approximate and unvalidated in the
UI, not as exact English speaker presets. All ten catalog vowels have a model;
unsupported future sounds still fall back to illustrations.

The exporter blends JD3 articulation parameters before running the upstream
geometry generator; it does not interpolate finished triangle meshes. TRAP uses
65% a + 35% E. PALM starts with a and retracts TCX by 0.45 cm and TBX by 0.25 cm.
STRUT uses 50% a + 50% @. LOT uses 65% a + 35% O, retains a's jaw angle, and
uses O's lip protrusion and lip distance. These are design recipes intended to
illustrate broad placement differences; they have not been fitted acoustically
or reviewed as British-English articulations. The manifest records every input
parameter, source preset, and blend weight. The six original meshes retain their
previous hashes. Duration is not geometry, and no pose measures the learner.

`VocalTractLabMeshes` reads versioned indexed meshes with source corner normals,
validates their bounds, and caches them. One fixed transform preserves relative
shape differences. `OralCavityModel` clips triangles and interpolates normals at
the midsagittal plane. The shared Compose canvas rotates and projects the mesh,
sorts triangles by depth, and interpolates vertex lighting. The meshes describe
vocal-tract boundary surfaces, not a complete external face or solid tongue.
The renderer uses painter sorting, so intersecting surfaces may show ordering
artifacts at some angles. It does not run the upstream synthesis engine.

Front and Side presets, drag rotation/tilt, bounded zoom, reset, and accessible
button alternatives remain available. Mesh loading has a retry state; phoneme
switching never labels a previous mesh as the newly selected sound. References
remain independent of microphone and camera data.

The GPL-covered model assets are accompanied by the pinned upstream source,
speaker input, exporter, scripts, manifest, and license in application resources.
**Model credits & license** shows attribution and the full license. See
`third_party/vocaltractlab/README.md` and `scripts/vtl/regenerate.sh` for provenance
and regeneration. Export omits the filling triangles hidden by the original
viewer, preserving the remaining coordinates and normals.

Verification covers ten distinct meshes, explicit derived-pose labels, unsupported
sound fallbacks, malformed files, finite geometry, exact cut-plane clipping,
sound switching, controls, recording independence, and narrow/wide layouts in
both themes with enlarged text. Rendered views are in
`composeApp/build/reports/mouth-position/model-*.png`.

## Live camera comparison

The Camera tab starts only after **Start camera** is pressed. Android uses
CameraX and bundled ML Kit face contours; iOS uses AVCaptureSession and Vision
face landmarks. The desktop build reports camera feedback as unavailable while
keeping references and acoustic practice usable. Camera frames are processed
on-device without recording or uploading them.

The learner sees a mirrored preview, a normalized live lip outline, the inner
lip opening as a percentage of outer mouth width, contour circularity, and the
selected vowel's reference image and placement cue. A temporary personal pose
can be captured for repeatability comparisons; it is not a verified target.
See [camera measurements](ARTICULATOR_CAMERA.md) for smoothing and metric limits.
This is a visible-lip comparison, not a
pronunciation score, an anatomical reconstruction, or calibrated jaw distance.
The older `FacialLandmarkAnalyzer` uses assumed geometry and fallback landmarks;
it is intentionally not used for live measurements.

`LipShapeAnalyzer` uses equal-scale image coordinates, compensates for small
head roll, and rejects missing/nonfinite/degenerate contours. Native adapters
reject small or oblique faces. Tracking loss immediately clears measurements;
a two-second freshness timeout also clears a stalled measurement. Session tokens
reject callbacks after stopping, denying permission, or starting a new session.
Camera resources are released on tab exit and backgrounding; returning from the
background requires an explicit restart. The iOS video session does not
reconfigure the microphone recorder's audio session.

Platform API references:
- [CameraX image analysis](https://developer.android.com/media/camera/camerax/analyze)
- [ML Kit face contours](https://developers.google.com/ml-kit/vision/face-detection/android)
- [Vision face landmarks](https://developer.apple.com/documentation/vision/vndetectfacelandmarksrequest)

Automated checks exercise measurement invariance, invalid inputs, stale-session
rejection, lifecycle cleanup, tracking-loss recovery, and narrow layouts in both
themes with enlarged text. Camera preview areas in the JVM render tests are test
surfaces, not captured frames. Before release, verify on physical Android and iOS
devices: permission grant/denial/retry, front-camera mirroring, portrait/landscape
rotation, low light and face loss, background/foreground, rapid tab changes, and
simultaneous microphone recording. Actual camera capture has not been validated
by those JVM tests.

## Optional iOS mouth-position prototype

Mouth position additionally offers an unreviewed sheep/ship prototype on iOS,
with an Illustration alternative. This is separate from the VocalTractLab
**3D model** tab described above. See [MOUTH_POSITION_3D_PROTOTYPE.md](MOUTH_POSITION_3D_PROTOTYPE.md)
for the supplied model's provenance, authored weights, offline bridge, and
remaining phonetic and device review gates. Existing VocalTractLab and Camera
references, licensing, progress storage, and lifecycle behavior remain available.
