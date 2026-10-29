#!/usr/bin/env python3
"""Explicitly generate Stories test speech; never writes app resources.

Uses the existing project's ElevenLabs connection and Flash v2 CMU phoneme tags.
Run from the model virtual environment. Existing fixtures are preserved unless
--force is requested. Generated speech is not a human pronunciation benchmark.
"""
import argparse
import json
from pathlib import Path
import re
import ssl
import urllib.request

from generate_elevenlabs_audio import VOICES, get_api_key, wrap_pcm_to_wav

PRONUNCIATIONS = {
    "sheep": "SH IY1 P", "ship": "SH IH1 P", "shape": "SH EY1 P",
    "pen": "P EH1 N", "pan": "P AE1 N", "pin": "P IH1 N",
    "heart": "HH AA1 T", "hut": "HH AH1 T", "heat": "HH IY1 T",
    "full": "F UH1 L", "fool": "F UW1 L", "fall": "F AO1 L",
    "pot": "P AA1 T", "port": "P AO1 T", "pat": "P AE1 T",
    "cat": "K AE1 T", "cut": "K AH1 T", "cot": "K AA1 T",
}
THIRD_WORDS = {
    "sheep_vs_ship": "shape", "pen_vs_pan": "pin", "heart_vs_hut": "heat", "fool_vs_full": "fall",
    "pot_vs_port": "pat", "pen_vs_pin": "pan", "cat_vs_cut": "cot",
}


def main():
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=root / "build/reports/stories/controlled")
    parser.add_argument("--voice", choices=VOICES.keys(), default="tutor")
    parser.add_argument("--story", action="append", help="Generate only this story ID; repeat for multiple stories")
    parser.add_argument("--natural-pronunciation", action="store_true",
                        help="Use the voice's natural pronunciation instead of American CMU tags (e.g. British LOT/THOUGHT)")
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()
    destination = args.output / args.voice
    destination.mkdir(parents=True, exist_ok=True)
    try:
        import certifi
        context = ssl.create_default_context(cafile=certifi.where())
    except ImportError:
        context = ssl.create_default_context()
    key = None
    generated = {}
    provenance_path = destination / "provenance.json"
    records = {record["file"]: record for record in json.loads(provenance_path.read_text())} if provenance_path.exists() else {}
    catalog = (root / "composeApp/src/commonMain/kotlin/com/minimalpairs/app/data/catalog/MeaningTrapCatalog.kt").read_text()
    stories = [dict(re.findall(r'(id|intendedWord|trapWord|targetPhrase) = "([^"\n]+)"', block))
               for block in catalog.split("ComicStory(")[1:]]
    if args.story and not set(args.story) <= {story["id"] for story in stories}:
        parser.error("Unknown story ID")
    for story in stories:
        if args.story and story["id"] not in args.story:
            continue
        for kind, word in (("intended", story["intendedWord"]), ("trap", story["trapWord"]),
                           ("outside", THIRD_WORDS[story["id"]]), ("unrelated", None)):
            if word is None:
                text = "The weather is sunny and warm today."
            else:
                tag = word if args.natural_pronunciation else f'<phoneme alphabet="cmu-arpabet" ph="{PRONUNCIATIONS[word]}">{word}</phoneme>'
                text = story["targetPhrase"].replace(story["intendedWord"], tag)
            path = destination / f'{story["id"]}_{kind}.wav'
            previous = records.get(path.name)
            if path.exists() and not args.force:
                if previous is None or previous.get("text") != text or previous.get("voice") != args.voice:
                    raise ValueError(f"Existing control has different or missing provenance: {path}. Use another output directory or --force.")
                print(path.name, flush=True)
                continue
            if not path.exists() or args.force:
                if text not in generated:
                    key = key or get_api_key()
                    request = urllib.request.Request(
                        f'https://api.elevenlabs.io/v1/text-to-speech/{VOICES[args.voice]["voice_id"]}?output_format=pcm_16000',
                        data=json.dumps({"text": text, "model_id": "eleven_flash_v2", "voice_settings": {
                            "stability": .75, "similarity_boost": .85, "style": 0., "use_speaker_boost": True,
                        }}).encode(), headers={"xi-api-key": key, "Content-Type": "application/json"}, method="POST")
                    with urllib.request.urlopen(request, context=context, timeout=90) as response:
                        generated[text] = wrap_pcm_to_wav(response.read())
                path.write_bytes(generated[text])
            records[path.name] = {"file": path.name, "text": text, "voice": args.voice,
                                  "voice_id": VOICES[args.voice]["voice_id"], "model": "eleven_flash_v2",
                                  "natural_pronunciation": args.natural_pronunciation}
            print(path.name, flush=True)
    provenance_path.write_text(json.dumps(list(records.values()), indent=2))


if __name__ == "__main__":
    main()
