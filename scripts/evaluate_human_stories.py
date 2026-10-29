#!/usr/bin/env python3
"""Evaluate consented human Stories recordings; this tool does not supply a dataset."""

import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import io
import json
import math
import os
from pathlib import Path
import re
import urllib.error
import urllib.parse
import urllib.request
import uuid
import wave


ROOT = Path(__file__).resolve().parents[1]
KINDS = {"intended", "trap", "ambiguous", "unrelated"}
QUALITY_CODES = {"uncertain_alignment", "noisy_audio", "clipped_audio", "no_clear_speech"}
THRESHOLDS = {"minimum_score": 80, "minimum_lead": 15}


def load_catalog(root=ROOT):
    """Read runtime metadata without importing model or generation dependencies."""
    source = (root / "composeApp/src/commonMain/kotlin/com/minimalpairs/app/data/catalog/MeaningTrapCatalog.kt").read_text()
    stories = {}
    for block in source.split("ComicStory(")[1:]:
        story = dict(re.findall(r'(id|intendedWord|intendedIpa|trapWord|trapIpa|targetPhrase|targetPhraseIpa) = "([^"\n]+)"', block))
        story["index"] = int(re.search(r"targetPhonemeIndex = (\d+)", block)[1])
        first, second = story["intendedIpa"].split(), story["trapIpa"].split()
        differences = [i for i, pair in enumerate(zip(first, second)) if pair[0] != pair[1]]
        if len(first) != len(second) or len(differences) != 1:
            raise ValueError(f"Invalid minimal pair for {story['id']}")
        word_index = differences[0]
        tokens = story["targetPhraseIpa"].split()
        index = story["index"]
        if not 0 <= index < len(tokens) or tokens[index] != first[word_index] or index < word_index or tokens[index - word_index:index - word_index + len(first)] != first:
            raise ValueError(f"Invalid target position for {story['id']}")
        pattern = re.compile(r"\b" + re.escape(story["intendedWord"]) + r"\b")
        if len(pattern.findall(story["targetPhrase"])) != 1 or story["id"] in stories:
            raise ValueError("Story IDs and target-word occurrences must be unique")
        story["tokens"] = tokens
        story["alternate_tokens"] = tokens.copy()
        story["alternate_tokens"][index] = second[word_index]
        story["alternate_phrase"] = pattern.sub(story["trapWord"], story["targetPhrase"])
        stories[story["id"]] = story
    spec_ids = re.findall(r'^  - id: "([^"\n]+)"', (root / "specs/stories/meaning_trap_stories.yaml").read_text(), re.MULTILINE)
    if not stories or len(spec_ids) != len(stories) or set(spec_ids) != set(stories):
        raise ValueError("Runtime catalog and story specification disagree")
    return stories


def require_text(value, label):
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{label} must be a nonempty string")
    return value.strip()


def read_audio(path):
    if path.stat().st_size > 1_000_000:
        raise ValueError(f"WAV exceeds the service's 1 MB limit: {path.name}")
    data = path.read_bytes()
    try:
        with wave.open(io.BytesIO(data), "rb") as audio:
            if (audio.getnchannels(), audio.getsampwidth(), audio.getframerate(), audio.getcomptype()) != (1, 2, 16000, "NONE"):
                raise ValueError("Expected 16 kHz, 16-bit, mono PCM WAV")
            frames = audio.getnframes()
            if not 1280 <= frames <= 480000 or len(audio.readframes(frames)) != frames * 2:
                raise ValueError("Expected an intact WAV between 80 ms and 30 seconds")
    except (wave.Error, EOFError) as error:
        raise ValueError(f"Invalid WAV: {path.name}") from error
    return data, frames / 16000


def load_manifest(path, stories):
    path = Path(path).resolve()
    manifest = json.loads(path.read_text())
    if not isinstance(manifest, dict) or type(manifest.get("schema_version")) is not int or manifest["schema_version"] != 1:
        raise ValueError("Manifest schema_version must be 1")
    require_text(manifest.get("dataset_id"), "dataset_id")
    require_text(manifest.get("provenance"), "provenance")
    recordings = manifest.get("recordings")
    if not isinstance(recordings, list) or not recordings:
        raise ValueError("No human recordings supplied; the human study remains pending")
    ids, pairs, clip_hashes, result = set(), set(), set(), []
    generated = (ROOT / "composeApp/src/commonMain/composeResources/files/audio").resolve()
    for original in recordings:
        if not isinstance(original, dict):
            raise ValueError("Every recording must be an object")
        row = dict(original)
        for field in ("recording_id", "speaker_id", "accent", "microphone", "annotation", "wav_path"):
            row[field] = require_text(row.get(field), field)
        if row["recording_id"] in ids:
            raise ValueError("Duplicate recording_id")
        ids.add(row["recording_id"])
        if row.get("source_type") != "human" or row.get("consent_confirmed") is not True:
            raise ValueError("Only recordings declared human with confirmed evaluation consent are accepted")
        if not isinstance(row.get("story_id"), str) or row["story_id"] not in stories or not isinstance(row.get("expected"), str) or row["expected"] not in KINDS:
            raise ValueError("Unknown story_id or expected label")
        wav_path = (path.parent / row["wav_path"]).resolve()
        if wav_path.is_relative_to(generated):
            raise ValueError("Bundled generated reference audio is not independent human speech")
        pair = (row["story_id"], wav_path)
        if pair in pairs:
            raise ValueError("A recording cannot be counted twice for the same story")
        pairs.add(pair)
        data, duration = read_audio(wav_path)
        digest = hashlib.sha256(data).hexdigest()
        clip_key = (row["story_id"], digest)
        if clip_key in clip_hashes:
            raise ValueError("Identical audio cannot be counted twice for the same story")
        clip_hashes.add(clip_key)
        row.update(audio=data, duration_seconds=duration, sha256=digest)
        result.append(row)
    return manifest, result


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def reject_json_constant(value):
    raise ValueError(f"Invalid JSON numeric constant: {value}")


def make_http_scorer(endpoint, function_key=None):
    url = urllib.parse.urlsplit(endpoint)
    if url.scheme not in ("http", "https") or not url.hostname or url.username or url.password or url.query or url.fragment:
        raise ValueError("Endpoint must be an HTTP(S) service base URL without credentials, query, or fragment")
    if url.scheme != "https" and (function_key or url.hostname not in ("localhost", "127.0.0.1", "::1")):
        raise ValueError("Use HTTPS for remote services and whenever a function key is supplied")
    target = endpoint.rstrip("/") + "/v1/score/phoneme"
    opener = urllib.request.build_opener(NoRedirect())

    def score(fields, audio):
        boundary = "human-stories-" + uuid.uuid4().hex
        chunks = []
        for key, value in fields.items():
            chunks.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode())
        chunks.extend([f'--{boundary}\r\nContent-Disposition: form-data; name="audio_file"; filename="reply.wav"\r\nContent-Type: audio/wav\r\n\r\n'.encode(), audio, f"\r\n--{boundary}--\r\n".encode()])
        headers = {"Content-Type": f"multipart/form-data; boundary={boundary}"}
        if function_key:
            headers["x-functions-key"] = function_key
        request = urllib.request.Request(target, data=b"".join(chunks), headers=headers, method="POST")
        try:
            response = opener.open(request, timeout=120)
        except urllib.error.HTTPError as error:
            response = error
        except (urllib.error.URLError, OSError) as error:
            return {"status": None, "body": None, "transport_error": type(error).__name__}
        with response:
            payload = response.read(2_000_001)
            try:
                body = json.loads(payload, parse_constant=reject_json_constant) if len(payload) <= 2_000_000 else None
            except (ValueError, UnicodeDecodeError):
                body = None
            return {"status": response.code, "body": body, "raw_body": payload.decode("utf-8", errors="replace")}
    return score


def finite(value, minimum, maximum):
    return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value) and minimum <= value <= maximum


def validate_response(body, tokens, index, alternate, duration):
    if not isinstance(body, dict):
        raise ValueError("Response is not a JSON object")
    phonemes = body.get("phonemes")
    if body.get("scoring_method") != "neural_ctc_phrase_contrast" or not isinstance(body.get("model_id"), str) or not body["model_id"].strip():
        raise ValueError("Phrase contrast scoring or model identity is missing")
    if (body.get("alternative_phoneme") != alternate or type(body.get("target_phoneme_index")) is not int or body["target_phoneme_index"] != index or
            not finite(body.get("contrast_evidence"), .1, 1) or not finite(body.get("overall_score"), 0, 100) or
            not isinstance(phonemes, list) or len(phonemes) != len(tokens)):
        raise ValueError("Invalid phrase scoring metadata")
    previous_end = 0
    for token, phoneme in zip(tokens, phonemes):
        if (not isinstance(phoneme, dict) or phoneme.get("phoneme") != token or
                not finite(phoneme.get("gop_score"), 0, 100) or
                not finite(phoneme.get("start_time"), 0, duration + .001) or
                not finite(phoneme.get("end_time"), 0, duration + .001) or
                phoneme["end_time"] <= phoneme["start_time"] or phoneme["start_time"] < previous_end):
            raise ValueError("Invalid phoneme scores or alignment")
        previous_end = phoneme["end_time"]
    if abs(body["overall_score"] - phonemes[index]["gop_score"]) > .001:
        raise ValueError("Phrase average cannot choose an ending")


def evaluate_recording(row, story, score):
    responses = []
    for phrase, tokens, alternate in (
        (story["targetPhrase"], story["tokens"], story["alternate_tokens"][story["index"]]),
        (story["alternate_phrase"], story["alternate_tokens"], story["tokens"][story["index"]]),
    ):
        fields = {"target_transcript": phrase, "expected_ipa": " ".join(tokens),
                  "target_phoneme_index": str(story["index"]), "utterance_kind": "phrase", "alternative_phoneme": alternate}
        try:
            response = score(fields, row["audio"])
        except Exception as error:
            response = {"status": None, "body": None, "transport_error": type(error).__name__}
        responses.append(response)
    result = {key: value for key, value in row.items() if key != "audio"}
    result.update(responses=responses, outcome=None)
    for response in responses:
        body = response.get("body")
        detail = body.get("detail") if isinstance(body, dict) else None
        quality = response.get("status") == 400 and (
            isinstance(detail, dict) and detail.get("code") in QUALITY_CODES or detail == "No audible speech detected")
        if response.get("status") != 200 and not quality:
            return dict(result, status="service_error")
    try:
        for response, tokens, alternate in zip(responses, (story["tokens"], story["alternate_tokens"]),
                                               (story["alternate_tokens"][story["index"]], story["tokens"][story["index"]])):
            if response["status"] == 200:
                validate_response(response["body"], tokens, story["index"], alternate, row["duration_seconds"])
        if any(response["status"] != 200 for response in responses):
            return dict(result, status="quality_rejected")
        first, second = (response["body"] for response in responses)
        first_region, second_region = first["phonemes"][story["index"]], second["phonemes"][story["index"]]
        if first["model_id"] != second["model_id"] or any(abs(first_region[key] - second_region[key]) > .001 for key in ("start_time", "end_time")) or abs(first["contrast_evidence"] - second["contrast_evidence"]) > .001:
            raise ValueError("Both directions must share model, region, and evidence")
        intended, trap = first_region["gop_score"], second_region["gop_score"]
        minimum, lead = THRESHOLDS["minimum_score"], THRESHOLDS["minimum_lead"]
        outcome = "intended" if intended >= minimum and intended - trap >= lead else "trap" if trap >= minimum and trap - intended >= lead else "uncertain"
        return dict(result, status="evaluated", outcome=outcome, model_id=first["model_id"])
    except (ValueError, TypeError, KeyError) as error:
        return dict(result, status="contract_error", error=str(error))


def metrics(rows):
    counts = Counter(row["status"] for row in rows)
    evaluated = [row for row in rows if row["status"] == "evaluated"]
    confident = [row for row in evaluated if row["outcome"] in ("intended", "trap")]
    false = sum(row["outcome"] != row["expected"] for row in confident)
    uncertain = sum(row["outcome"] == "uncertain" for row in evaluated)
    retry_denominator = len(evaluated) + counts["quality_rejected"]
    return {"attempts": len(rows), "speakers": len({row["speaker_id"] for row in rows}),
            "evaluated": len(evaluated), "confident_endings": len(confident), "false_endings": false,
            "uncertain": uncertain, "quality_rejected": counts["quality_rejected"],
            "service_errors": counts["service_error"], "contract_errors": counts["contract_error"],
            "false_ending_rate_per_evaluated": false / len(evaluated) if evaluated else None,
            "false_ending_rate_per_confident": false / len(confident) if confident else None,
            "retry_denominator": retry_denominator,
            "retry_rate": (uncertain + counts["quality_rejected"]) / retry_denominator if retry_denominator else None,
            "expected_labels": dict(Counter(row["expected"] for row in rows))}


def build_report(manifest, rows, stories, manifest_sha256):
    if not rows:
        raise ValueError("No evaluated recordings; human study remains pending")
    groups = {}
    for field in ("story_id", "speaker_id", "accent", "microphone"):
        groups[field] = {name: metrics([row for row in rows if row[field] == name]) for name in sorted({row[field] for row in rows})}
    missing = {story_id: sorted(KINDS - {row["expected"] for row in rows if row["story_id"] == story_id}) for story_id in stories}
    return {"schema_version": 1, "dataset_id": manifest["dataset_id"], "provenance": manifest["provenance"],
            "created_at": datetime.now(timezone.utc).isoformat(), "manifest_sha256": manifest_sha256,
            "status": "evaluation_report_not_calibration" if any(row["status"] == "evaluated" for row in rows) else "no_valid_comparisons", "thresholds": THRESHOLDS,
            "model_ids": sorted({row["model_id"] for row in rows if row.get("model_id")}),
            "note": "Experimental confidence, not pronunciation accuracy. Source declarations require a collection audit; this tool cannot prove natural speech or consent.",
            "overall": metrics(rows), "groups": groups, "missing_expected_labels_by_story": missing,
            "results": rows}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--endpoint", default="http://127.0.0.1:8000", help="Service base URL; recordings are uploaded here")
    parser.add_argument("--function-key-env", default="PHONEME_SCORER_FUNCTION_KEY", help="Environment variable containing the optional service key")
    parser.add_argument("--output", type=Path, default=ROOT / "build/reports/stories/human-evaluation.json")
    args = parser.parse_args(argv)
    try:
        stories = load_catalog()
        manifest, recordings = load_manifest(args.manifest, stories)
        protected_paths = {args.manifest.resolve()} | {(args.manifest.resolve().parent / row["wav_path"]).resolve() for row in recordings}
        if args.output.resolve() in protected_paths:
            raise ValueError("Report output must not overwrite the manifest or input audio")
        score = make_http_scorer(args.endpoint, os.environ.get(args.function_key_env))
        results = [evaluate_recording(row, stories[row["story_id"]], score) for row in recordings]
        report = build_report(manifest, results, stories, hashlib.sha256(args.manifest.read_bytes()).hexdigest())
        report["endpoint"] = args.endpoint
        report["source_sha256"] = {name: hashlib.sha256((ROOT / name).read_bytes()).hexdigest() for name in (
            "scripts/evaluate_human_stories.py",
            "composeApp/src/commonMain/kotlin/com/minimalpairs/app/data/catalog/MeaningTrapCatalog.kt",
            "composeApp/src/commonMain/kotlin/com/minimalpairs/app/domain/MeaningTrapEvaluator.kt")}
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False) + "\n")
    except (ValueError, OSError, TypeError) as error:
        parser.exit(2, f"Evaluation not run: {error}\n")
    summary = report["overall"]
    print(json.dumps({"report": str(args.output), "overall": summary}, ensure_ascii=False))
    return int(bool(not summary["evaluated"] or summary["service_errors"] or summary["contract_errors"] or summary["false_endings"]))


if __name__ == "__main__":
    raise SystemExit(main())
