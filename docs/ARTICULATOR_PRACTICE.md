# Articulator Lab practice selection

Speak uses the Pocket studio layout. Its compact header opens the sound-pair
picker with every contrast available in Listen. The two target sounds share
one selector, with phonemes and example words side by side. Short Map, Mouth,
3D, and Camera tabs retain their descriptive accessibility labels. Consonants
offer Mouth and Camera only. See the [rendered 390 by 640 dp Speak content
preview](../design/speak-pocket-studio/implemented.png).

The first visit opens Camera with capture off. The 196 dp practice mirror,
small lip reference, and collapsed measurements fit on a 320 by 640 dp content
area at standard text sizes. Start camera is centered in the idle mirror;
Turn off and camera status overlay an active preview. Recording credits and
detailed acoustic and camera feedback are available inside Sound measurements.
Large accessibility text stacks the header, uses taller content and two rows
of view tabs, and places playback controls in two rows with stacked action
icons. Reference content remains scrollable.

The record/stop control, elapsed recording time, and microphone errors remain
visible at the bottom while reference content scrolls. The compact actions
place icons beside their labels, with a wider Record button before a recording
exists. Play mine remains the primary action after a recording is available.
This allows recording while viewing the camera or 3D comparison without
scrolling away to find Stop.

Contrast, example pair, and target sound are locked during recording,
preparation, playback, scoring, and session cleanup. Choosing a new contrast
resets the example to its first pair and selects its first sound. Choosing a
new example programmatically retains the selected sound. Any changed selection
clears the previous recording, acoustic measurements, score, and feedback.
Pending progress save errors remain visible until the repository resolves them.

A targeted Progress practice link restores its requested contrast and phoneme
even if that retained Speak screen was manually changed. An ordinary Speak tab
visit retains its in-session selection.

Live acoustic readings clear when an unvoiced buffer arrives or when no fresh
analysis arrives for 750 ms. Completed recording analysis replaces the last live
frame before scoring, so comparison remains available if scoring fails.

Regression checks: `ArticulatorLabComponentTest` covers selected-word scoring and
history, invalid selections, recording/scoring locks, retained routes, and stale
acoustic readings. `ArticulatorSelectionUiTest` exercises pair/target controls,
disabled selectors, compact camera layout, safe areas, and large accessibility
text. It writes `build/reports/articulator/pocket-studio-390-false.png` and
other size/theme captures alongside `selection.png`. `LipCameraUiTest` covers
camera lifecycle, measurement clearing, and narrow layouts.
