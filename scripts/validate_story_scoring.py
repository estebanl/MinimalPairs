"""Check Stories phrase scoring using bundled speech and the local model.

These generated references are integration diagnostics, not independent human
pronunciation validation. All four bundled replies must choose their intended
ending. Optional controls exercise both endings and out-of-pair speech.
"""
import argparse
import json
import os
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--model", type=Path, default=root / "build/models/phoneme-ctc")
parser.add_argument("--controls-dir", type=Path, help="Extra WAVs named <story-id>_<intended|trap|outside|unrelated>.wav, optionally in voice subfolders")
parser.add_argument("--allow-uncertain-controls", action="store_true", help="Report unclear extra controls as retries; bundled references must still resolve")
args = parser.parse_args()
os.environ["PHONEME_MODEL_DIR"] = str(args.model.resolve())
os.environ["HF_HUB_OFFLINE"] = "1"
os.environ["TRANSFORMERS_OFFLINE"] = "1"
sys.path.insert(0, str(root / "backend"))

from fastapi.testclient import TestClient
from app.main import app

catalog = (root / "composeApp/src/commonMain/kotlin/com/minimalpairs/app/data/catalog/MeaningTrapCatalog.kt").read_text()
client = TestClient(app)
stories = []
for block in catalog.split("ComicStory(")[1:]:
    story = dict(re.findall(r'(id|intendedWord|trapWord|trapIpa|targetPhrase|targetPhraseIpa) = "([^"\n]+)"', block))
    story["index"] = int(re.search(r"targetPhonemeIndex = (\d+)", block)[1])
    stories.append(story)


def score_case(story, audio, label, expected, require_decision=True):
    index = story["index"]
    tokens = story["targetPhraseIpa"].split()
    alternate = list(tokens)
    alternate[index] = story["trapIpa"].split()[1]
    responses = []
    for transcript, ipa in (
        (story["targetPhrase"], tokens),
        (story["targetPhrase"].replace(story["intendedWord"], story["trapWord"]), alternate),
    ):
        response = client.post("/v1/score/phoneme", data={
            "target_transcript": transcript, "expected_ipa": " ".join(ipa),
            "target_phoneme_index": str(index), "utterance_kind": "phrase",
            "alternative_phoneme": alternate[index] if ipa == tokens else tokens[index],
        }, files={"audio_file": ("phrase.wav", audio, "audio/wav")})
        body = response.json()
        if response.status_code == 200:
            assert body["scoring_method"] == "neural_ctc_phrase_contrast"
            assert .1 <= body["contrast_evidence"] <= 1.
            assert body["alternative_phoneme"] == (alternate[index] if ipa == tokens else tokens[index])
            assert body["target_phoneme_index"] == index
            assert body["overall_score"] == body["phonemes"][index]["gop_score"]
            assert [p["phoneme"] for p in body["phonemes"]] == ipa
        else:
            detail = body.get("detail")
            assert response.status_code == 400 and isinstance(detail, dict) and detail.get("code") in (
                "uncertain_alignment", "noisy_audio", "clipped_audio", "no_clear_speech"
            ), body
        responses.append({"status": response.status_code, "body": body})
    ending = "uncertain"
    if all(r["status"] == 200 for r in responses):
        intended, trap = (r["body"]["overall_score"] for r in responses)
        if intended >= 80 and intended - trap >= 15:
            ending = "intended"
        elif trap >= 80 and trap - intended >= 15:
            ending = "trap"
    scores = [round(r["body"]["overall_score"], 1) if r["status"] == 200 else "uncertain" for r in responses]
    retry = not require_decision and ending == "uncertain" and expected != "uncertain"
    status = "PASS" if ending == expected else "RETRY" if retry else f"FAIL expected {expected}"
    print(label, scores, ending, status, flush=True)
    return {"story": story["id"], "case": label, "expected": expected, "ending": ending,
            "retry": retry, "passed": ending == expected or retry, "responses": responses}


results = []
for story in stories:
    audio = (root / f'composeApp/src/commonMain/composeResources/files/audio/meaning_trap/{story["id"]}_phrase.wav').read_bytes()
    results.append(score_case(story, audio, f'bundled/{story["id"]}', "intended"))
if args.controls_dir:
    for path in sorted(args.controls_dir.rglob("*.wav")):
        for story in stories:
            prefix = story["id"] + "_"
            if path.stem.startswith(prefix):
                kind = path.stem[len(prefix):]
                assert kind in ("intended", "trap", "outside", "unrelated"), path
                results.append(score_case(story, path.read_bytes(), str(path.relative_to(args.controls_dir)),
                                          kind if kind in ("intended", "trap") else "uncertain",
                                          require_decision=not args.allow_uncertain_controls))
                break
        else:
            raise ValueError(f"Unknown control file: {path}")

report = root / "build/reports/stories/reference-scoring.json"
report.parent.mkdir(parents=True, exist_ok=True)
report.write_text(json.dumps({"note": __doc__, "results": results}, ensure_ascii=False, indent=2))
assert len(stories) == 4, "Catalog parsing missed a story"
failures = [r["case"] for r in results if not r["passed"]]
assert not failures, f"Unexpected story decisions: {failures}"
if args.controls_dir:
    for story in stories:
        controls = [r for r in results if r["story"] == story["id"] and not r["case"].startswith("bundled/")]
        for expected in ("intended", "trap"):
            if any(r["expected"] == expected for r in controls):
                assert any(r["expected"] == expected and r["ending"] == expected for r in controls), (
                    f'{story["id"]}: no additional {expected} control resolved; retries do not establish branch coverage')
print("Cases:", len(results), "Retries:", sum(r["retry"] for r in results), "Intended:", sum(r["ending"] == "intended" for r in results),
      "Trap:", sum(r["ending"] == "trap" for r in results), "Uncertain:", sum(r["ending"] == "uncertain" for r in results))
