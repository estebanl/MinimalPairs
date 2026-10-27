# Target and observed sound in the 3D tab

The 3D tab compares the selected vowel's acoustic target with current or reviewed
F1/F2 measurements. An outlined target area and filled observed dot appear on a
schematic formant plane above the anatomical reference. Lower F1 maps upward
(higher tongue tendency); higher F2 maps forward. These are display coordinates,
not measured tongue positions. The text provides the same measurements and
directional cues for users who cannot see the diagram.

The comparison is available even when a reference mesh is unavailable or still
loading. Nonfinite, absent, unordered, or out-of-display-domain formants produce
an explicit no-measurement state, not a default dot. The screen supplies the
selected contrast's bounds; switching sound, word, or contrast clears the
previous observation. Live unvoiced analysis clears live measurements.

## Scope and limitations

The original specification's actual vocal tract reconstruction is not possible
from the current microphone and front camera. F1/F2 do not uniquely determine
tongue shape; camera lip contours do not observe the internal vocal tract.
Consequently the measured sound is compared in acoustic space and does not deform
the reference mesh. The UI calls the result an acoustic placement estimate and
explains this limitation. Neither arbitrary millimetres nor inferred anatomical
surfaces are presented as observations. A true anatomical comparison requires a
validated articulatory model and appropriate measurement hardware/data.

The fixed schematic domain is F1 150–1500 Hz and F2 500–4000 Hz, with F2 greater
than F1. Values outside that domain are unavailable rather than clamped into a
misleading target position. This is a display validity guard, not a clinical
normality range. No separate pronunciation score is produced by this view.

## Verification

`AcousticPlacementComparisonTest` checks invalid input, orientation, all catalog
target ranges, and directional guidance. `AcousticPlacementComparisonUiTest`
checks unavailable/live/reviewed states, sound changes and narrow layouts in both
themes with enlarged text, and writes screenshots to
`composeApp/build/reports/mouth-position/comparison-*.png`.
