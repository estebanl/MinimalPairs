# ElevenLabs vowel examples

These files replace the previous Wikimedia recordings. No Wikimedia audio was
used as generation input. The previous recordings and credits are backed up
locally in `build/phoneme-recordings-before-elevenlabs/` (not packaged).

Generated using ElevenLabs Eleven v4 and the Daniel preset voice. See
`generation.json` for exact inputs, model, voice ID, settings, checksums and
technical measurements. Audio is bundled as 16 kHz mono signed 16-bit PCM WAV.
No resampling, time stretching or pitch changes were applied to the API output.

Regenerate candidates with:

```sh
python3 scripts/generate_elevenlabs_phonemes.py --output-dir build/new-phoneme-candidates
```

The generator does not overwrite app assets. Review candidates before copying
them into this directory. Technical validation does not certify pronunciation;
the current generation still requires phonetic listening review. In particular,
check /ɪ/–/iː/, /ʊ/–/uː/ and /ɒ/–/ɔː/ remain distinct.

These are AI-generated speech examples, not human phonetic reference recordings.
Provider terms: https://elevenlabs.io/terms-of-use
