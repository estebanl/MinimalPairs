import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch


SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
spec = importlib.util.spec_from_file_location("story_controls", SCRIPTS / "generate_story_controls.py")
controls = importlib.util.module_from_spec(spec)
spec.loader.exec_module(controls)


class StoryControlsTest(unittest.TestCase):
    def run_generator(self, directory, natural=True):
        args = ["generate_story_controls.py", "--story", "pot_vs_port", "--voice", "professor_smith",
                "--output", str(directory)]
        if natural:
            args.append("--natural-pronunciation")
        # Explicit synthetic bytes only: these tests do not call a speech service.
        with patch.object(sys, "argv", args), patch.object(controls, "get_api_key", return_value="test-key"), \
                patch.object(controls.urllib.request, "urlopen", side_effect=lambda *a, **k: io.BytesIO(b"\x01\x00" * 1600)) as upload:
            controls.main()
            return upload.call_count

    def test_story_selection_and_natural_voice_metadata_survive_a_cached_rerun(self):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            self.assertEqual(4, self.run_generator(directory))
            destination = directory / "professor_smith"
            records = json.loads((destination / "provenance.json").read_text())
            self.assertEqual(4, len(list(destination.glob("*.wav"))))
            self.assertEqual({"pot_vs_port_" + kind + ".wav" for kind in ("intended", "trap", "outside", "unrelated")},
                             {record["file"] for record in records})
            self.assertTrue(all(record["natural_pronunciation"] for record in records))
            self.assertTrue(all("<phoneme" not in record["text"] for record in records))
            self.assertEqual(0, self.run_generator(directory))
            self.assertEqual(records, json.loads((destination / "provenance.json").read_text()))

    def test_changed_generation_mode_cannot_relabel_existing_audio_provenance(self):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            self.run_generator(directory)
            path = directory / "professor_smith/provenance.json"
            original = path.read_bytes()
            with self.assertRaisesRegex(ValueError, "different or missing provenance"):
                self.run_generator(directory, natural=False)
            self.assertEqual(original, path.read_bytes())


if __name__ == "__main__":
    unittest.main()
