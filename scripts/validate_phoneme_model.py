#!/usr/bin/env python3
"""Run opt-in, offline real-checkpoint smoke checks and record observed confidence.

Bundled reference speech and generated vowels are not a learner evaluation set.
No numerical pronunciation-quality threshold is asserted.
"""

import argparse
import io
from importlib.metadata import version
import json
import os
from pathlib import Path
import sys
import time
import wave

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "backend"))


def wav_bytes(samples):
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(16000)
        output.writeframes(samples.astype("<i2").tobytes())
    return buffer.getvalue()


def synthetic_vowel(f1, f2):
    import numpy as np
    from scipy.signal import lfilter

    count = 6400
    source = np.zeros(count)
    source[::119] = 1
    signal = np.zeros(count)
    for frequency, bandwidth, amplitude in ((f1, 80, .5), (f2, 100, .35), (2800, 120, .15)):
        radius = np.exp(-np.pi * bandwidth / 16000)
        signal += amplitude * lfilter([1], [1, -2 * radius * np.cos(2 * np.pi * frequency / 16000), radius**2], source)
    envelope = np.minimum(1, np.minimum(np.arange(count) / 640, (count - np.arange(count)) / 960))
    signal *= envelope
    return wav_bytes(signal / max(abs(signal)) * 20000)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path, default=ROOT / "build/models/phoneme-ctc")
    parser.add_argument("--output", type=Path, default=ROOT / "build/reports/phoneme-model/smoke.json")
    args = parser.parse_args()
    os.environ["PHONEME_MODEL_DIR"] = str(args.model.resolve())
    manifest = json.loads((args.model / "provisioning.json").read_text())
    os.environ["PHONEME_MODEL_ID"] = f"{manifest['repository']}@{manifest['revision']}"
    os.environ["HF_HUB_OFFLINE"] = "1"
    import numpy as np
    from fastapi.testclient import TestClient
    from app.ctc_scorer import get_model
    from app.main import app

    started = time.monotonic()
    get_model()
    load_seconds = time.monotonic() - started
    client = TestClient(app)
    results = []

    def score(name, kind, audio, ipa, expected_status=(200,)):
        started = time.monotonic()
        response = client.post("/v1/score/phoneme", data={"target_transcript": name, "expected_ipa": ipa},
                               files={"audio_file": ("sample.wav", audio, "audio/wav")})
        body = response.json()
        result = {"sample": name, "kind": kind, "expected_ipa": ipa, "status": response.status_code,
                  "seconds": time.monotonic() - started, "response": body}
        results.append(result)
        assert response.status_code in expected_status, result
        if response.status_code == 200:
            assert body["scoring_method"] == "neural_ctc"
            assert 0 <= body["overall_score"] <= 100
            assert [item["phoneme"] for item in body["phonemes"]] == ipa.split()
        return body

    words = ROOT / "composeApp/src/commonMain/composeResources/files/audio/words"
    for word, onset, vowel, alternate, coda in (
        ("sheep", "ʃ", "iː", "ɪ", "p"), ("ship", "ʃ", "ɪ", "iː", "p"),
        ("bad", "b", "æ", "e", "d"), ("bed", "b", "e", "æ", "d"),
        ("heart", "h", "ɑː", "ʌ", "t"), ("hut", "h", "ʌ", "ɑː", "t"),
        ("full", "f", "ʊ", "uː", "l"), ("fool", "f", "uː", "ʊ", "l"),
        ("cot", "k", "ɒ", "ɔː", "t"), ("caught", "k", "ɔː", "ɒ", "t"),
    ):
        audio = (words / f"{word}.wav").read_bytes()
        for target in (vowel, alternate):
            score(word, "bundled whole-word reference", audio, f"{onset} {target} {coda}")

    for name, f1, f2 in (("generated fleece", 310, 2350), ("generated kit", 450, 1900)):
        audio = synthetic_vowel(f1, f2)
        for target in ("iː", "ɪ"):
            body = score(name, "unsupported isolated-vowel task; expected guarded rejection", audio, target, expected_status=(400,))
            assert "does not support isolated-phoneme scoring" in body["detail"]
    for word in ("sheep", "ship"):
        with wave.open(str(words / f"{word}.wav")) as source:
            samples = np.frombuffer(source.readframes(source.getnframes()), dtype="<i2")
        for start, end in ((.18, .38), (.2, .35)):
            crop = wav_bytes(samples[int(start * 16000):int(end * 16000)])
            for target in ("iː", "ɪ"):
                body = score(f"{word} [{start}, {end}] seconds", "unsupported isolated-vowel crop; expected guarded rejection",
                      crop, target, expected_status=(400,))
                assert "does not support isolated-phoneme scoring" in body["detail"]
    score("silence", "invalid input", wav_bytes(np.zeros(6400)), "iː", expected_status=(400,))
    noise = np.random.default_rng(20261027).normal(0, 2000, 6400).clip(-32768, 32767)
    score("seeded noise", "negative control", wav_bytes(noise), "iː", expected_status=(200, 400))
    report = {"model": manifest, "load_seconds": load_seconds, "health": client.get("/health").json(),
              "runtime": {"python": sys.version, **{package: version(package) for package in
                          ("torch", "transformers", "numpy", "scipy", "fastapi", "httpx")}},
              "scope": "Actual offline inference smoke test; not calibrated learner-speech validation. CTC times are emission spans.",
              "results": results}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(f"{len(results)} real API checks completed ({sum(r['status'] == 200 for r in results)} scored, "
          f"{sum(r['status'] == 400 for r in results)} rejected); evidence: {args.output}")
    print(f"Model load: {load_seconds:.2f}s; no model-quality thresholds asserted.")


if __name__ == "__main__":
    main()
