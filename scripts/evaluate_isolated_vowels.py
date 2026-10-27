#!/usr/bin/env python3
"""Offline independent-corpus comparison; never trains or changes runtime scoring.

Uses all adult FLEECE/KIT and TRAP/DRESS nuclei in the pinned Hillenbrand1995
archive. This is American native speech cropped from /hVd/, not British learner
isolated speech. Output retains rejections and every clip's outcome.
"""

import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import sys
import time
from urllib.request import urlopen
import wave
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
REVISION = "6dd44decc8ef5b537cbb54732a3b00c8fa652e65"
SOURCE = f"https://raw.githubusercontent.com/santiagobarreda/hillenbrand_et_al_1995/{REVISION}"
ARCHIVE_SHA256 = "2560548591e3a726c88549b6dc9d226995616c26874d601c69fee8b9eee9d730"
TARGETS = {"iy": ("iː", "ɪ"), "ih": ("ɪ", "iː"), "ae": ("æ", "ɛ"), "eh": ("ɛ", "æ")}


def read_audio(audio):
    import numpy as np
    with wave.open(io.BytesIO(audio)) as stream:
        if (stream.getframerate(), stream.getnchannels(), stream.getsampwidth()) != (16000, 1, 2):
            raise ValueError("Unexpected corpus audio format")
        return np.frombuffer(stream.readframes(stream.getnframes()), dtype="<i2").astype(np.float32) / 32768


def read_nucleus(audio, start_ms, end_ms):
    samples = read_audio(audio)
    start = round(start_ms * 16)
    end = round(end_ms * 16)
    if not 0 <= start < end <= len(samples):
        raise ValueError("Invalid annotated nucleus interval")
    return samples[start:end]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, default=ROOT / "build/validation/hillenbrand")
    parser.add_argument("--download", action="store_true", help="Explicitly provision the pinned 22 MB archive")
    parser.add_argument("--model", type=Path, default=ROOT / "build/models/phoneme-ctc")
    parser.add_argument("--output", type=Path, default=ROOT / "build/reports/phoneme-model/isolated-corpus.json")
    args = parser.parse_args()
    archive_path = args.dataset / "h95-alldata.zip"
    if args.download:
        args.dataset.mkdir(parents=True, exist_ok=True)
        for name in ("h95-alldata.zip", "README.md", "LICENSE"):
            if not (args.dataset / name).exists():
                with urlopen(f"{SOURCE}/{name}", timeout=60) as response:
                    (args.dataset / name).write_bytes(response.read())
    if not archive_path.is_file():
        parser.error("Dataset absent; provision explicitly with --download")
    if hashlib.sha256(archive_path.read_bytes()).hexdigest() != ARCHIVE_SHA256:
        parser.error("Dataset checksum does not match pinned corpus")
    os.environ["PHONEME_MODEL_DIR"] = str(args.model.resolve())
    os.environ["HF_HUB_OFFLINE"] = "1"
    sys.path.insert(0, str(ROOT / "backend"))
    import numpy as np
    from app.ctc_scorer import InvalidTarget, align_posteriors, get_model
    from app.ctc_evaluation import sequence_log_probability

    model = get_model()
    results = []
    controls = []
    started = time.monotonic()
    with ZipFile(archive_path) as archive:
        # Read only named members. No untrusted archive paths are extracted.
        tables = {"m": ZipFile(io.BytesIO(archive.read("men.zip"))),
                  "w": ZipFile(io.BytesIO(archive.read("women.zip")))}
        for line in archive.read("timedata.dat").decode("ascii").splitlines():
            fields = line.split()
            if not fields or not re.fullmatch(r"[mw]\d\d(?:iy|ih|ae|eh)", fields[0]):
                continue
            name = fields[0]
            start_ms, end_ms = map(float, fields[1:3])
            audio = tables[name[0]].read(name + ".wav")
            samples = read_nucleus(audio, start_ms, end_ms)
            expected, alternate = TARGETS[name[3:]]
            logits = model.infer_logits(samples)
            ids = [model.vocab[token] for token in (expected, alternate)]
            baseline = []
            rejection = None
            try:
                for token in ids:
                    baseline.append(align_posteriors(logits, [token], model.blank_id, len(samples) / 16000)[0]["gop_score"])
            except InvalidTarget as error:
                rejection = str(error)
            forward = [sequence_log_probability(logits, [token], model.blank_id) for token in ids]
            # Rank only. Do not turn a relative preference into a pronunciation grade.
            results.append({"file": name, "speaker": name[:3], "group": name[0],
                "expected": expected, "alternate": alternate, "start_ms": start_ms, "end_ms": end_ms,
                "duration_ms": len(samples) / 16, "baseline_scores": baseline if rejection is None else None,
                "rms": float(np.sqrt(np.mean(samples ** 2))), "peak": float(np.max(np.abs(samples))),
                "baseline_rejection": rejection,
                "baseline_correct": rejection is None and baseline[0] > baseline[1],
                "forward_log_probabilities": forward,
                "forward_correct": forward[0] > forward[1],
                "forward_correct_with_same_rejection_policy": rejection is None and forward[0] > forward[1],
                "blank_sequence_log_probability": sequence_log_probability(logits, [], model.blank_id),
                "greedy_nonblank_frames": int(np.count_nonzero(np.argmax(logits, axis=1) != model.blank_id))})
            # Prespecified full-word controls: speaker01 of each adult group,
            # every evaluated vowel. No selection based on model outcomes.
            if name[1:3] == "01":
                whole = read_audio(audio)
                full_logits = model.infer_logits(whole)
                full_scores = []
                error = None
                try:
                    for token in ids:
                        aligned = align_posteriors(full_logits, [model.vocab["h"], token, model.vocab["d"]],
                                                   model.blank_id, len(whole) / 16000)
                        full_scores.append(float(np.mean([part["gop_score"] for part in aligned])))
                except InvalidTarget as problem:
                    error = str(problem)
                controls.append({"file": name, "expected": expected, "alternate": alternate,
                    "kind": "full hVd word; prespecified m01/w01 control",
                    "duration_ms": len(whole) / 16, "rms": float(np.sqrt(np.mean(whole ** 2))),
                    "peak": float(np.max(np.abs(whole))), "baseline_scores": full_scores if error is None else None,
                    "baseline_rejection": error, "baseline_correct": error is None and full_scores[0] > full_scores[1],
                    "greedy_nonblank_frames": int(np.count_nonzero(np.argmax(full_logits, axis=1) != model.blank_id))})
            if len(results) % 50 == 0:
                print(f"Evaluated {len(results)} nuclei", flush=True)
        for table in tables.values():
            table.close()

    def summarize(rows):
        accepted = sum(row["baseline_rejection"] is None for row in rows)
        return {"total": len(rows), "accepted": accepted, "rejected": len(rows) - accepted,
            "baseline_correct": sum(row["baseline_correct"] for row in rows),
            "forward_correct_without_rejection": sum(row["forward_correct"] for row in rows),
            "forward_correct_with_same_rejection_policy": sum(row["forward_correct_with_same_rejection_policy"] for row in rows)}

    per_vowel = {vowel: summarize([r for r in results if r["expected"] == vowel]) for vowel in sorted({r["expected"] for r in results})}
    if len(results) != 372 or len(controls) != 8:
        raise ValueError("Pinned corpus selection is incomplete")
    report = {"corpus_source": SOURCE, "archive_sha256": ARCHIVE_SHA256,
        "model": json.loads((args.model / "provisioning.json").read_text()),
        "selection": "All adult iy/ih/ae/eh files with published vowel-nucleus start/end times; no tuning or training",
        "limitations": ["American /hVd/ nuclei are not isolated British learner speech",
            "Independence from checkpoint pretraining is not established",
            "Relative ranking is not calibrated quality; forward ranking without rejection can rank noise"],
        "seconds": time.monotonic() - started, "summary": summarize(results),
        "per_vowel": per_vowel, "results": results, "full_word_controls": controls}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report["summary"], indent=2))
    print(f"Evidence: {args.output}")


if __name__ == "__main__":
    main()
