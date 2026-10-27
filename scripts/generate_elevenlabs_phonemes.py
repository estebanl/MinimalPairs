#!/usr/bin/env python3
"""Generate isolated IPA vowel candidates with ElevenLabs; review before bundling.

Uses the same environment/Doppler credentials as generate_elevenlabs_audio.py.
Example: python3 scripts/generate_elevenlabs_phonemes.py --output-dir build/phoneme-candidates
Requires ElevenLabs text-to-speech permission (no model-list permission needed).
"""
import argparse
import hashlib
import json
import math
import struct
from datetime import datetime, timezone
from pathlib import Path

from generate_elevenlabs_audio import (
    REPO_ROOT, VOICES, get_api_key, synthesize_pcm16k, wrap_pcm_to_wav,
)

# The catalog's /e/ denotes DRESS [ɛ], rather than cardinal [e].
PHONEMES = {
    "i_long": "iː", "ih": "ɪ", "ae": "æ", "eh": "ɛ",
    "ah_long": "ɑː", "uh": "ʌ", "oo_short": "ʊ", "oo_long": "uː",
    "o_short": "ɒ", "aw_long": "ɔː",
}


def validate_pcm(pcm: bytes) -> dict:
    if not pcm or len(pcm) % 2:
        raise ValueError("Expected nonempty signed 16-bit PCM")
    samples = struct.unpack(f"<{len(pcm) // 2}h", pcm)
    duration = len(samples) / 16000
    peak = max(abs(x) for x in samples)
    rms = math.sqrt(sum(x * x for x in samples) / len(samples))
    if not 0.2 <= duration <= 5 or rms < 50 or peak >= 32767:
        raise ValueError(f"Audio requires review: duration={duration:.2f}s, peak={peak}, rms={rms:.1f}")
    # Technical checks cannot establish phonetic accuracy; listening review is required.
    return {"duration_seconds": duration, "peak": peak, "rms": round(rms, 2)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=REPO_ROOT / "build/phoneme-candidates")
    parser.add_argument("--voice-id", default=VOICES["professor_smith"]["voice_id"])
    parser.add_argument("--model-id", default="eleven_v4")
    args = parser.parse_args()
    if args.output_dir.exists() and any(args.output_dir.iterdir()):
        parser.error("Use an empty output directory to preserve existing candidates")
    key = get_api_key()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    manifest = {
        "provider": "ElevenLabs", "model_id": args.model_id, "voice_id": args.voice_id,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "format": "16000 Hz, mono, signed 16-bit PCM WAV",
        "voice_settings": {"stability": 0.65, "similarity_boost": 0.80, "style": 0.0, "use_speaker_boost": True},
        "phonetic_listening_review": "pending", "recordings": [],
    }
    for name, ipa in PHONEMES.items():
        text = f"/{ipa}/"
        pcm = synthesize_pcm16k(key, args.voice_id, text, model_id=args.model_id)
        metrics = validate_pcm(pcm)
        wav = wrap_pcm_to_wav(pcm)
        (args.output_dir / f"{name}.wav").write_bytes(wav)
        manifest["recordings"].append({"file": f"{name}.wav", "ipa": ipa, "text": text,
            "sha256": hashlib.sha256(wav).hexdigest(), **metrics})
        (args.output_dir / "generation.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n")
        print(f"Generated /{ipa}/: {metrics['duration_seconds']:.2f}s", flush=True)


if __name__ == "__main__":
    main()
