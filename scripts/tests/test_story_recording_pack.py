import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch


scripts = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("story_recording_pack", scripts / "prepare_story_recording_pack.py")
pack = importlib.util.module_from_spec(spec)
with patch.object(sys, "path", [str(scripts), *sys.path]):
    spec.loader.exec_module(pack)


class StoryRecordingPackTest(unittest.TestCase):
    def setUp(self):
        self.stories = pack.load_catalog()
        self.plan = pack.build_plan(self.stories)

    def test_phrase_pairs_and_both_ipa_sequences_match_the_runtime_scoring_catalog(self):
        self.assertEqual(set(self.stories), {target["story_id"] for target in self.plan["targets"]})
        for target in self.plan["targets"]:
            with self.subTest(story=target["story_id"]):
                story = self.stories[target["story_id"]]
                self.assertEqual(story["targetPhrase"], target["phrase_a"])
                self.assertEqual(story["alternate_phrase"], target["phrase_b"])
                self.assertEqual(story["targetPhraseIpa"], target["phrase_a_ipa"])
                self.assertEqual(story["alternate_tokens"], target["phrase_b_ipa"].split())
                a, b = target["phrase_a_ipa"].split(), target["phrase_b_ipa"].split()
                self.assertEqual([target["target_phoneme_index"]], [i for i in range(len(a)) if a[i] != b[i]])
                self.assertEqual(b[story["index"]], target["alternative_phoneme"])
        banquet = next(t for t in self.plan["targets"] if t["story_id"] == "fool_vs_full")
        self.assertEqual("No thank you, I am completely fool.", banquet["phrase_b"])

    def test_each_session_has_two_of_each_reading_plus_review_and_unrelated_candidates(self):
        self.assertEqual(42, self.plan["clips_per_speaker_microphone_session"])
        self.assertEqual(42, len({task["task_id"] for task in self.plan["tasks"]}))
        for story_id in self.stories:
            rows = [row for row in self.plan["tasks"] if row["story_id"] == story_id]
            self.assertEqual(["intended_prompt", "alternate_prompt", "alternate_prompt", "intended_prompt",
                              "review_candidate", "unrelated_prompt"], [row["prompt_condition"] for row in rows])
            self.assertTrue(all("expected" not in row for row in rows))
            self.assertTrue(all(row["status"] == "not_recorded" and not row["recording_id"] for row in rows))

    def test_counterbalancing_changes_order_without_changing_targets_or_extra_tasks(self):
        reverse = pack.build_plan(self.stories, "BAAB")
        self.assertEqual(self.plan["targets"], reverse["targets"])
        for normal, alternate in zip(self.plan["tasks"], reverse["tasks"]):
            if normal["take"] <= 4:
                self.assertNotEqual(normal["prompt_condition"], alternate["prompt_condition"])
            else:
                self.assertEqual(normal, alternate)
        with self.assertRaises(ValueError):
            pack.build_plan(self.stories, "AAAA")

    def test_new_stories_require_explicit_unrelated_control_prompts(self):
        with self.assertRaisesRegex(ValueError, "Update unrelated prompts"):
            pack.build_plan({**self.stories, "new_story": {}})

    def test_metadata_never_assumes_consent_or_speaker_split(self):
        metadata = pack.blank_metadata()
        speaker = metadata["speakers"][0]
        self.assertFalse(speaker["evaluation_consent_confirmed"])
        self.assertFalse(speaker["service_upload_consent_confirmed"])
        self.assertEqual("unassigned", speaker["speaker_split"])
        self.assertEqual("", speaker["accent_self_description"])
        self.assertEqual("", speaker["consent_record_reference"])
        self.assertEqual("", metadata["microphones"][0]["microphone_id"])
        self.assertEqual("unknown", metadata["microphones"][0]["noise_suppression"])
        self.assertEqual("", metadata["sessions"][0]["recorded_at_with_timezone"])
        self.assertFalse({"name", "email", "phone", "date_of_birth"} & speaker.keys())

    def test_review_forms_withhold_prompt_identity_and_do_not_preassign_labels(self):
        forms = pack.blank_annotations()
        reviewer = forms["reviewer_form"]
        self.assertEqual("", reviewer["label"])
        self.assertEqual("", forms["adjudication_form"]["final_expected"])
        self.assertEqual({"intended", "trap", "ambiguous", "unrelated"}, set(forms["allowed_labels"]))
        self.assertFalse({"speaker_id", "accent", "microphone", "prompt_condition", "score"} & reviewer.keys())
        self.assertIn("recording_id", forms["private_coordinator_map"])

    def test_existing_filled_pack_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "filled-pack"
            path.mkdir()
            filled = path / "filled-metadata.json"
            filled.write_text("private collected metadata")
            with self.assertRaisesRegex(ValueError, "already exists"):
                pack.create_pack(path)
            self.assertEqual("private collected metadata", filled.read_text())


if __name__ == "__main__":
    unittest.main()
