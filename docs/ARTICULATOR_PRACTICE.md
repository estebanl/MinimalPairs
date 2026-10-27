# Articulator Lab practice selection

The Speak screen offers all six catalog contrasts in the Contrast menu. Select
an example word pair, then one of its sounds. The selected example appears in
all reference views and is sent to scoring and saved speaking history.

The record/stop control, elapsed recording time, and microphone errors remain
visible at the bottom while reference content scrolls. This allows recording
while viewing the camera or 3D comparison without scrolling away to find Stop.

Contrast, example pair, and target sound are locked during recording,
preparation, playback, scoring, and session cleanup. Choosing a new contrast
resets the example to its first pair and selects its first sound. Choosing a
new example retains the selected sound. Any changed selection clears the
previous recording, acoustic measurements, score, and feedback. Pending progress
save errors remain visible until the repository resolves them.

A targeted Progress practice link restores its requested contrast and phoneme
even if that retained Speak screen was manually changed. An ordinary Speak tab
visit retains its in-session selection.

Live acoustic readings clear when an unvoiced buffer arrives or when no fresh
analysis arrives for 750 ms. Completed recording analysis replaces the last live
frame before scoring, so comparison remains available if scoring fails.

Regression checks: `ArticulatorLabComponentTest` covers selected-word scoring and
history, invalid selections, recording/scoring locks, retained routes, and stale
acoustic readings. `ArticulatorSelectionUiTest` exercises example/target controls
and disabled selectors, writing `build/reports/articulator/selection.png`.
