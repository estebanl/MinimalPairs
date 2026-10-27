# Consonant word similarity

Speak scores every consonant contrast in `ListeningContrastsCatalog` offline,
using the selected word and the other word from the same pair. The result is
labeled **Word similarity**, not consonant recognition or pronunciation accuracy.
The comparison includes the entire word; pairs such as shoe/measure differ in
more than one sound. Speaker, accent, microphone, and noise differences affect it.

## Signal and score

- Input and both bundled references are 16 kHz, signed 16-bit mono PCM.
- Reject recordings shorter than 80 ms or longer than 30 seconds. Require six
  overlapping 25 ms frames with non-DC RMS of at least 0.002 (normalized PCM).
  No voiced-vowel or formant requirement applies to consonants.
- Trim edge silence using the larger of RMS 0.002 and 3% of peak frame RMS;
  retain silence inside the word, including stop closures.
- Use 25 ms Hann windows at 10 ms intervals, 512-point spectra, and 26 triangular
  mel-frequency bands spanning 80–7600 Hz. Normalize each frame to its peak band,
  floor relative power at 0.0001, and compare normalized log energy.
- Bound each sequence to 200 frames. Dynamic time warping uses frame RMS spectral
  distance and divides accumulated cost by the longer sequence length.
- Let T be distance to the selected word and A distance to the alternative.
  The score is `100 * A / (T + A) * exp(-T)`, bounded to 0–100. If both distances
  are zero, use a relative match of 0.5. This is a heuristic similarity scale,
  not a calibrated probability. Exact target audio gives 100; the exact opposite
  reference gives 0; an equal match gives at most 50.

## App behavior

The scorer returns method `word_similarity`, with no invented phoneme boundaries
or per-phoneme scores. It needs no remote service. Missing/invalid references,
silent or unusable input, cancellation, and invalid results never become fallback
scores. Audio remains available for replay after a scoring failure.

Valid results use the existing speaking-history persistence and retry flow, with
contrast ID, word, selected phoneme, score, duration, and time. Progress labels
consonant results as experimental word similarity and reopens the selected sound.
Vowel scoring and listening progress are unchanged.

Regression tests exercise all bundled consonant example pairs, gain and silence
changes, invalid input, missing resources, cancellation, UI labels, saved targets,
and navigation. These checks do not establish accuracy across human speakers.
