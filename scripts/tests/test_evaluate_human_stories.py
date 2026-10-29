import copy
import importlib.util
import io
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch
import wave


path = Path(__file__).resolve().parents[1] / "evaluate_human_stories.py"
spec = importlib.util.spec_from_file_location("human_stories", path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class HumanStoriesEvaluationTest(unittest.TestCase):
    def setUp(self):
        self.catalog = module.load_catalog()
        self.story = self.catalog["sheep_vs_ship"]
        self.row = dict(recording_id="fixture-1", speaker_id="speaker-a", accent="test-accent",
                        microphone="test-mic", annotation="Test fixture only, not human data",
                        expected="intended", story_id=self.story["id"], duration_seconds=1,
                        audio=b"test-fixture", sha256="test-hash")

    def responses(self, intended=99, trap=1):
        responses = []
        for tokens, score, alternate in (
            (self.story["tokens"], intended, self.story["alternate_tokens"][self.story["index"]]),
            (self.story["alternate_tokens"], trap, self.story["tokens"][self.story["index"]]),
        ):
            body = dict(overall_score=score, model_id="test-model", scoring_method="neural_ctc_phrase_contrast",
                        target_phoneme_index=self.story["index"], alternative_phoneme=alternate, contrast_evidence=.4,
                        phonemes=[dict(phoneme=token, gop_score=score if i == self.story["index"] else 50,
                                       start_time=i * .03, end_time=i * .03 + .02) for i, token in enumerate(tokens)])
            responses.append(dict(status=200, body=body))
        return responses

    def evaluate(self, responses, row=None):
        supplied = iter(copy.deepcopy(responses))
        return module.evaluate_recording(row or self.row, self.story, lambda fields, audio: next(supplied))

    def test_score_and_margin_thresholds_match_the_app(self):
        for intended, trap, expected in ((80, 20, "intended"), (79.99, 20, "uncertain"),
                                        (80, 70, "uncertain"), (20, 80, "trap"), (50, 50, "uncertain")):
            with self.subTest(intended=intended, trap=trap):
                result = self.evaluate(self.responses(intended, trap))
                self.assertEqual("evaluated", result["status"])
                self.assertEqual(expected, result["outcome"])

    def test_wrong_models_regions_evidence_and_invalid_scores_cannot_choose_endings(self):
        mutations = [
            lambda b: b.update(model_id="other-model"),
            lambda b: b.update(contrast_evidence=.6),
            lambda b: b["phonemes"][self.story["index"]].update(end_time=.361),
            lambda b: b.update(overall_score=float("nan")),
            lambda b: b.update(overall_score=35),
            lambda b: b.update(target_phoneme_index=0),
            lambda b: b.update(alternative_phoneme="ʌ"),
            lambda b: b["phonemes"][0].update(start_time=-.01),
            lambda b: b["phonemes"][0].update(end_time=2),
            lambda b: b["phonemes"][0].update(phoneme="unknown"),
        ]
        for mutate in mutations:
            responses = self.responses()
            mutate(responses[1]["body"])
            result = self.evaluate(responses)
            self.assertEqual("contract_error", result["status"])
            self.assertIsNone(result["outcome"])

    def test_service_errors_are_not_counted_as_uncertain_speech(self):
        quality = dict(status=400, body={"detail": {"code": "uncertain_alignment", "message": "unclear"}})
        service = dict(status=503, body={"detail": "model unavailable"})
        self.assertEqual("quality_rejected", self.evaluate([quality, quality])["status"])
        self.assertEqual("service_error", self.evaluate([quality, service])["status"])
        self.assertEqual("service_error", self.evaluate([dict(status=200, body=None), service])["status"])
        report = module.metrics([self.evaluate([quality, service])])
        self.assertEqual(1, report["service_errors"])
        self.assertEqual(0, report["retry_denominator"])
        self.assertIsNone(report["retry_rate"])

    def test_request_transcripts_change_only_the_selected_vowel(self):
        requests = []
        responses = iter(self.responses())
        def score(fields, audio):
            requests.append(fields)
            return next(responses)
        module.evaluate_recording(self.row, self.story, score)
        before, after = (request["expected_ipa"].split() for request in requests)
        self.assertEqual([self.story["index"]], [i for i in range(len(before)) if before[i] != after[i]])
        self.assertEqual("Can I have that ship, please?", requests[1]["target_transcript"])
        self.assertTrue(all(request["utterance_kind"] == "phrase" for request in requests))

    def test_false_endings_and_retries_have_explicit_denominators_and_groups(self):
        correct = self.evaluate(self.responses())
        wrong = self.evaluate(self.responses(), dict(self.row, recording_id="2", expected="unrelated", speaker_id="speaker-b"))
        uncertain = self.evaluate(self.responses(50, 50), dict(self.row, recording_id="3", expected="ambiguous"))
        quality = dict(self.row, recording_id="4", status="quality_rejected", outcome=None)
        outage = dict(self.row, recording_id="5", status="service_error", outcome=None)
        report = module.build_report({"dataset_id": "test-only", "provenance": "Automated test fixtures"},
                                     [correct, wrong, uncertain, quality, outage], self.catalog, "manifest-hash")
        overall = report["overall"]
        self.assertEqual(5, overall["attempts"])
        self.assertEqual(1, overall["false_endings"])
        self.assertEqual(1 / 3, overall["false_ending_rate_per_evaluated"])
        self.assertEqual(.5, overall["false_ending_rate_per_confident"])
        self.assertEqual(4, overall["retry_denominator"])
        self.assertEqual(.5, overall["retry_rate"])
        self.assertEqual(1, report["groups"]["speaker_id"]["speaker-b"]["false_endings"])
        self.assertEqual(set(self.catalog) - {self.story["id"]},
                         {story for story, labels in report["missing_expected_labels_by_story"].items() if len(labels) == 4})
        self.assertEqual(["test-model"], report["model_ids"])

    def fixture_manifest(self, directory):
        audio_path = directory / "test-fixture.wav"
        with wave.open(str(audio_path), "wb") as audio:
            audio.setparams((1, 2, 16000, 0, "NONE", "not compressed"))
            audio.writeframes(struct.pack("<h", 1000) * 16000)
        row = {key: value for key, value in self.row.items() if key not in ("audio", "duration_seconds", "sha256")}
        row.update(source_type="human", consent_confirmed=True, wav_path=audio_path.name)
        return dict(schema_version=1, dataset_id="test-only", provenance="Test fixtures; not human study data", recordings=[row])

    def test_manifest_rejects_empty_synthetic_unconsented_missing_and_duplicate_records(self):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            original = self.fixture_manifest(directory)
            cases = []
            for field, value in (("source_type", "synthetic"), ("consent_confirmed", False), ("accent", ""),
                                 ("microphone", None), ("story_id", "unknown"), ("expected", "success")):
                changed = copy.deepcopy(original)
                changed["recordings"][0][field] = value
                cases.append(changed)
            cases.extend([dict(original, recordings=[]), dict(original, recordings=original["recordings"] * 2)])
            for manifest in cases:
                manifest_path = directory / "manifest.json"
                manifest_path.write_text(json.dumps(manifest))
                with self.subTest(manifest=manifest), self.assertRaises(ValueError):
                    module.load_manifest(manifest_path, self.catalog)
            manifest_path.write_text(json.dumps(original))
            _, rows = module.load_manifest(manifest_path, self.catalog)
            self.assertEqual(1, len(rows))
            self.assertEqual(1, rows[0]["duration_seconds"])
            self.assertEqual(64, len(rows[0]["sha256"]))

    def test_invalid_audio_and_bundled_generated_references_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            manifest = self.fixture_manifest(directory)
            manifest_path = directory / "manifest.json"
            manifest_path.write_text(json.dumps(manifest))
            (directory / "test-fixture.wav").write_bytes(b"not a WAV")
            with self.assertRaises(ValueError):
                module.load_manifest(manifest_path, self.catalog)
            manifest["recordings"][0]["wav_path"] = str(module.ROOT / "composeApp/src/commonMain/composeResources/files/audio/meaning_trap/sheep_vs_ship_phrase.wav")
            manifest_path.write_text(json.dumps(manifest))
            with self.assertRaisesRegex(ValueError, "generated"):
                module.load_manifest(manifest_path, self.catalog)

    def test_same_audio_in_another_file_is_not_an_independent_same_story_observation(self):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            manifest = self.fixture_manifest(directory)
            (directory / "copied.wav").write_bytes((directory / "test-fixture.wav").read_bytes())
            copied = dict(manifest["recordings"][0], recording_id="different-id", wav_path="copied.wav")
            manifest["recordings"].append(copied)
            manifest_path = directory / "manifest.json"
            manifest_path.write_text(json.dumps(manifest))
            with self.assertRaisesRegex(ValueError, "Identical audio"):
                module.load_manifest(manifest_path, self.catalog)

    def test_all_quality_rejections_preserve_raw_evidence_but_do_not_claim_success(self):
        quality = dict(status=400, body={"detail": {"code": "uncertain_alignment"}})
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            manifest_path = directory / "manifest.json"
            manifest_path.write_text(json.dumps(self.fixture_manifest(directory)))
            output = directory / "report.json"
            with patch.object(module, "make_http_scorer", return_value=lambda fields, audio: quality):
                exit_code = module.main([str(manifest_path), "--output", str(output)])
            report = json.loads(output.read_text())
            self.assertEqual(1, exit_code)
            self.assertEqual("no_valid_comparisons", report["status"])
            self.assertEqual(1, report["overall"]["quality_rejected"])
            self.assertEqual([quality, quality], report["results"][0]["responses"])

    def test_empty_manifest_does_not_write_a_success_report(self):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            manifest = directory / "empty.json"
            manifest.write_text(json.dumps(dict(schema_version=1, dataset_id="pending", provenance="No data", recordings=[])))
            output = directory / "report.json"
            with self.assertRaises(SystemExit) as raised:
                module.main([str(manifest), "--output", str(output)])
            self.assertEqual(2, raised.exception.code)
            self.assertFalse(output.exists())

    def test_remote_uploads_require_https_and_credentials_are_never_in_url(self):
        for endpoint, key in (("http://example.com", None), ("http://127.0.0.1:8000", "test-key"),
                              ("https://user:password@example.com", None), ("https://example.com?key=secret", None)):
            with self.subTest(endpoint=endpoint), self.assertRaises(ValueError):
                module.make_http_scorer(endpoint, key)

    def test_output_cannot_overwrite_input_manifest(self):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            manifest_path = directory / "manifest.json"
            contents = json.dumps(self.fixture_manifest(directory))
            manifest_path.write_text(contents)
            with self.assertRaises(SystemExit) as raised:
                module.main([str(manifest_path), "--output", str(manifest_path)])
            self.assertEqual(2, raised.exception.code)
            self.assertEqual(contents, manifest_path.read_text())


if __name__ == "__main__":
    unittest.main()
