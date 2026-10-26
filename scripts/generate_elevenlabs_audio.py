#!/usr/bin/env python3
"""
Generates 16kHz 16-bit mono PCM WAV audio assets for MinimalPairs using ElevenLabs TTS.
Fetches the ElevenLabs API key via Doppler (secret `ELEVENLABS` in project `minimal-pairs`, config `dev`,
or from `ELEVENLABS` / `ELEVENLABS_API_KEY` environment variables).

Usage:
    doppler run --project minimal-pairs --config dev -- python3 scripts/generate_elevenlabs_audio.py
    # or directly (auto-invokes doppler if env var is not set):
    python3 scripts/generate_elevenlabs_audio.py [--force] [--words-only]
"""

import argparse
import json
import os
import struct
import subprocess
import sys
import time
from pathlib import Path

SAMPLE_RATE = 16000
CHANNELS = 1
BITS_PER_SAMPLE = 16

REPO_ROOT = Path(__file__).resolve().parent.parent
OUTPUT_BASE = REPO_ROOT / "composeApp" / "src" / "commonMain" / "composeResources" / "files" / "audio"

# Voice definitions (verified from ElevenLabs account voices)
VOICES = {
    # Clear American Educator for Minimal Pair Words, Prosody Sentences, and User Target Phrases
    "tutor": {
        "voice_id": "XrExE9yKIg1WjnnlVkGX",  # Matilda - Knowledgeable, Professional (American, Educational)
        "name": "Matilda (American Tutor)",
    },
    # Meaning Trap NPC Voices
    "farmer_giles": {
        "voice_id": "pqHfZKP75CvOlQylNhV4",  # Bill - Wise, Mature, Balanced (Old, American)
        "name": "Bill (Old Farmer Giles)",
    },
    "professor_smith": {
        "voice_id": "onwK4e9ZLuTAKqWW03F9",  # Daniel - Steady Broadcaster (Formal, British)
        "name": "Daniel (Strict Professor Smith)",
    },
    "cupids_florist": {
        "voice_id": "cgSgspJ2msm6clMCkdW9",  # Jessica - Playful, Bright, Warm (American)
        "name": "Jessica (Cupid's Florist)",
    },
    "emperors_butler": {
        "voice_id": "JBFqnCBsd6RMkjVDRZzb",  # George - Warm, Captivating Storyteller (Mature, British)
        "name": "George (The Emperor's Butler)",
    },
}

# Unique practice words across ListeningContrastsCatalog, including existing vowel drills.
# Use Flash v2 with CMU Arpabet tags, which ElevenLabs recommends over IPA for v2.
# Keep IPA as the human-readable pronunciation reference and explicit CMU vowels to
# preserve contrasts such as cot/caught in the American tutor voice.
# https://elevenlabs.io/docs/overview/capabilities/text-to-speech/best-practices
MINIMAL_PAIR_WORDS = [
    # 1. i_vs_I (/iː/ vs /ɪ/)
    {"word": "sheep", "ipa": "ʃiːp", "cmu": "SH IY1 P"},
    {"word": "ship", "ipa": "ʃɪp", "cmu": "SH IH1 P"},
    {"word": "feet", "ipa": "fiːt", "cmu": "F IY1 T"},
    {"word": "fit", "ipa": "fɪt", "cmu": "F IH1 T"},
    {"word": "seat", "ipa": "siːt", "cmu": "S IY1 T"},
    {"word": "sit", "ipa": "sɪt", "cmu": "S IH1 T"},
    {"word": "leak", "ipa": "liːk", "cmu": "L IY1 K"},
    {"word": "lick", "ipa": "lɪk", "cmu": "L IH1 K"},
    {"word": "cheeks", "ipa": "tʃiːks", "cmu": "CH IY1 K S"},
    {"word": "chicks", "ipa": "tʃɪks", "cmu": "CH IH1 K S"},
    # 2. ae_vs_e (/æ/ vs /e/)
    {"word": "bad", "ipa": "bæd", "cmu": "B AE1 D"},
    {"word": "bed", "ipa": "bɛd", "cmu": "B EH1 D"},
    {"word": "man", "ipa": "mæn", "cmu": "M AE1 N"},
    {"word": "men", "ipa": "mɛn", "cmu": "M EH1 N"},
    {"word": "pan", "ipa": "pæn", "cmu": "P AE1 N"},
    {"word": "pen", "ipa": "pɛn", "cmu": "P EH1 N"},
    {"word": "sad", "ipa": "sæd", "cmu": "S AE1 D"},
    {"word": "said", "ipa": "sɛd", "cmu": "S EH1 D"},
    {"word": "cattle", "ipa": "ˈkætəl", "cmu": "K AE1 T AH0 L"},
    {"word": "kettle", "ipa": "ˈkɛtəl", "cmu": "K EH1 T AH0 L"},
    # 3. a_vs_uh (/ɑː/ vs /ʌ/)
    {"word": "heart", "ipa": "hɑːɹt", "cmu": "HH AA1 R T"},
    {"word": "hut", "ipa": "hʌt", "cmu": "HH AH1 T"},
    {"word": "calm", "ipa": "kɑːm", "cmu": "K AA1 M"},
    {"word": "come", "ipa": "kʌm", "cmu": "K AH1 M"},
    {"word": "dark", "ipa": "dɑːɹk", "cmu": "D AA1 R K"},
    {"word": "duck", "ipa": "dʌk", "cmu": "D AH1 K"},
    {"word": "march", "ipa": "mɑːɹtʃ", "cmu": "M AA1 R CH"},
    {"word": "much", "ipa": "mʌtʃ", "cmu": "M AH1 CH"},
    {"word": "barn", "ipa": "bɑːɹn", "cmu": "B AA1 R N"},
    {"word": "bun", "ipa": "bʌn", "cmu": "B AH1 N"},
    # 4. u_vs_uu (/ʊ/ vs /uː/)
    {"word": "full", "ipa": "fʊl", "cmu": "F UH1 L"},
    {"word": "fool", "ipa": "fuːl", "cmu": "F UW1 L"},
    {"word": "pull", "ipa": "pʊl", "cmu": "P UH1 L"},
    {"word": "pool", "ipa": "puːl", "cmu": "P UW1 L"},
    {"word": "look", "ipa": "lʊk", "cmu": "L UH1 K"},
    {"word": "luke", "ipa": "luːk", "cmu": "L UW1 K"},
    {"word": "soot", "ipa": "sʊt", "cmu": "S UH1 T"},
    {"word": "suit", "ipa": "suːt", "cmu": "S UW1 T"},
    {"word": "stood", "ipa": "stʊd", "cmu": "S T UH1 D"},
    {"word": "stewed", "ipa": "stuːd", "cmu": "S T UW1 D"},
    # 5. o_vs_or (/ɑː/ vs /ɔː/)
    {"word": "cot", "ipa": "kɑːt", "cmu": "K AA1 T"},
    {"word": "caught", "ipa": "kɔːt", "cmu": "K AO1 T"},
    {"word": "shot", "ipa": "ʃɑːt", "cmu": "SH AA1 T"},
    {"word": "short", "ipa": "ʃɔːɹt", "cmu": "SH AO1 R T"},
    {"word": "pot", "ipa": "pɑːt", "cmu": "P AA1 T"},
    {"word": "port", "ipa": "pɔːɹt", "cmu": "P AO1 R T"},
    {"word": "spot", "ipa": "spɑːt", "cmu": "S P AA1 T"},
    {"word": "sport", "ipa": "spɔːɹt", "cmu": "S P AO1 R T"},
    {"word": "fox", "ipa": "fɑːks", "cmu": "F AA1 K S"},
    {"word": "forks", "ipa": "fɔːɹks", "cmu": "F AO1 R K S"},
    # 6. e_vs_I (/e/ vs /ɪ/) - ("pen" and "sit" already listed above)
    {"word": "pin", "ipa": "pɪn", "cmu": "P IH1 N"},
    {"word": "ten", "ipa": "tɛn", "cmu": "T EH1 N"},
    {"word": "tin", "ipa": "tɪn", "cmu": "T IH1 N"},
    {"word": "bet", "ipa": "bɛt", "cmu": "B EH1 T"},
    {"word": "bit", "ipa": "bɪt", "cmu": "B IH1 T"},
    {"word": "set", "ipa": "sɛt", "cmu": "S EH1 T"},
    {"word": "dead", "ipa": "dɛd", "cmu": "D EH1 D"},
    {"word": "did", "ipa": "dɪd", "cmu": "D IH1 D"},
    # Additional Ship or Sheep vowel examples.
    {"word": "peel", "ipa": "piːl", "cmu": "P IY1 L"},
    {"word": "pill", "ipa": "pɪl", "cmu": "P IH1 L"},
    {"word": "bean", "ipa": "biːn", "cmu": "B IY1 N"},
    {"word": "bin", "ipa": "bɪn", "cmu": "B IH1 N"},
    {"word": "leave", "ipa": "liːv", "cmu": "L IY1 V"},
    {"word": "live", "ipa": "lɪv", "cmu": "L IH1 V"},  # Verb, not the adjective /laɪv/.
    {"word": "heel", "ipa": "hiːl", "cmu": "HH IY1 L"},
    {"word": "hill", "ipa": "hɪl", "cmu": "HH IH1 L"},
    {"word": "bat", "ipa": "bæt", "cmu": "B AE1 T"},
    {"word": "send", "ipa": "sɛnd", "cmu": "S EH1 N D"},
    {"word": "sand", "ipa": "sænd", "cmu": "S AE1 N D"},
    {"word": "cat", "ipa": "kæt", "cmu": "K AE1 T"},
    {"word": "cut", "ipa": "kʌt", "cmu": "K AH1 T"},
    {"word": "hat", "ipa": "hæt", "cmu": "HH AE1 T"},
    {"word": "match", "ipa": "mætʃ", "cmu": "M AE1 CH"},
    {"word": "ankle", "ipa": "ˈæŋkəl", "cmu": "AE1 NG K AH0 L"},
    {"word": "uncle", "ipa": "ˈʌŋkəl", "cmu": "AH1 NG K AH0 L"},
    {"word": "don", "ipa": "dɑːn", "cmu": "D AA1 N"},
    {"word": "dawn", "ipa": "dɔːn", "cmu": "D AO1 N"},
    {"word": "foot", "ipa": "fʊt", "cmu": "F UH1 T"},
    {"word": "boot", "ipa": "buːt", "cmu": "B UW1 T"},
    # Stops: /p b/, /t d/, /k g/.
    {"word": "pear", "ipa": "pɛɹ", "cmu": "P EH1 R"},
    {"word": "bear", "ipa": "bɛɹ", "cmu": "B EH1 R"},
    {"word": "pack", "ipa": "pæk", "cmu": "P AE1 K"},
    {"word": "back", "ipa": "bæk", "cmu": "B AE1 K"},
    {"word": "pig", "ipa": "pɪɡ", "cmu": "P IH1 G"},
    {"word": "big", "ipa": "bɪɡ", "cmu": "B IH1 G"},
    {"word": "rope", "ipa": "ɹoʊp", "cmu": "R OW1 P"},
    {"word": "robe", "ipa": "ɹoʊb", "cmu": "R OW1 B"},
    {"word": "two", "ipa": "tuː", "cmu": "T UW1"},
    {"word": "do", "ipa": "duː", "cmu": "D UW1"},
    {"word": "tear", "ipa": "tɪɹ", "cmu": "T IH1 R"},  # Eye tear; rhymes with dear.
    {"word": "dear", "ipa": "dɪɹ", "cmu": "D IH1 R"},
    {"word": "cart", "ipa": "kɑːɹt", "cmu": "K AA1 R T"},
    {"word": "card", "ipa": "kɑːɹd", "cmu": "K AA1 R D"},
    {"word": "wet", "ipa": "wɛt", "cmu": "W EH1 T"},
    {"word": "wed", "ipa": "wɛd", "cmu": "W EH1 D"},
    {"word": "coat", "ipa": "koʊt", "cmu": "K OW1 T"},
    {"word": "goat", "ipa": "ɡoʊt", "cmu": "G OW1 T"},
    {"word": "class", "ipa": "klæs", "cmu": "K L AE1 S"},
    {"word": "glass", "ipa": "ɡlæs", "cmu": "G L AE1 S"},
    {"word": "bag", "ipa": "bæɡ", "cmu": "B AE1 G"},
    {"word": "cold", "ipa": "koʊld", "cmu": "K OW1 L D"},
    {"word": "gold", "ipa": "ɡoʊld", "cmu": "G OW1 L D"},
    # Fricatives and affricates.
    {"word": "fan", "ipa": "fæn", "cmu": "F AE1 N"},
    {"word": "van", "ipa": "væn", "cmu": "V AE1 N"},
    {"word": "leaf", "ipa": "liːf", "cmu": "L IY1 F"},
    {"word": "few", "ipa": "fjuː", "cmu": "F Y UW1"},
    {"word": "view", "ipa": "vjuː", "cmu": "V Y UW1"},
    {"word": "ferry", "ipa": "ˈfɛɹi", "cmu": "F EH1 R IY0"},
    {"word": "very", "ipa": "ˈvɛɹi", "cmu": "V EH1 R IY0"},
    {"word": "think", "ipa": "θɪŋk", "cmu": "TH IH1 NG K"},
    {"word": "sink", "ipa": "sɪŋk", "cmu": "S IH1 NG K"},
    {"word": "thin", "ipa": "θɪn", "cmu": "TH IH1 N"},
    {"word": "this", "ipa": "ðɪs", "cmu": "DH IH1 S"},
    {"word": "sue", "ipa": "suː", "cmu": "S UW1"},
    {"word": "zoo", "ipa": "zuː", "cmu": "Z UW1"},
    {"word": "price", "ipa": "pɹaɪs", "cmu": "P R AY1 S"},
    {"word": "prize", "ipa": "pɹaɪz", "cmu": "P R AY1 Z"},
    {"word": "bus", "ipa": "bʌs", "cmu": "B AH1 S"},
    {"word": "buzz", "ipa": "bʌz", "cmu": "B AH1 Z"},
    {"word": "sip", "ipa": "sɪp", "cmu": "S IH1 P"},
    {"word": "zip", "ipa": "zɪp", "cmu": "Z IH1 P"},
    {"word": "slip", "ipa": "slɪp", "cmu": "S L IH1 P"},
    {"word": "shoe", "ipa": "ʃuː", "cmu": "SH UW1"},
    {"word": "measure", "ipa": "ˈmɛʒɚ", "cmu": "M EH1 ZH ER0"},
    {"word": "chair", "ipa": "tʃɛɹ", "cmu": "CH EH1 R"},
    {"word": "jam", "ipa": "dʒæm", "cmu": "JH AE1 M"},
    {"word": "cheap", "ipa": "tʃiːp", "cmu": "CH IY1 P"},
    # Liquids and glides.
    {"word": "light", "ipa": "laɪt", "cmu": "L AY1 T"},
    {"word": "right", "ipa": "ɹaɪt", "cmu": "R AY1 T"},
    {"word": "grass", "ipa": "ɡɹæs", "cmu": "G R AE1 S"},
    {"word": "fly", "ipa": "flaɪ", "cmu": "F L AY1"},
    {"word": "fry", "ipa": "fɹaɪ", "cmu": "F R AY1"},
    {"word": "belly", "ipa": "ˈbɛli", "cmu": "B EH1 L IY0"},
    {"word": "berry", "ipa": "ˈbɛɹi", "cmu": "B EH1 R IY0"},
    {"word": "vet", "ipa": "vɛt", "cmu": "V EH1 T"},
    {"word": "wine", "ipa": "waɪn", "cmu": "W AY1 N"},
    {"word": "vine", "ipa": "vaɪn", "cmu": "V AY1 N"},
    {"word": "west", "ipa": "wɛst", "cmu": "W EH1 S T"},
    {"word": "vest", "ipa": "vɛst", "cmu": "V EH1 S T"},
    {"word": "wheel", "ipa": "wiːl", "cmu": "W IY1 L"},
    {"word": "veal", "ipa": "viːl", "cmu": "V IY1 L"},
]

# 3 Prosody Sentences from ProsodyCatalog.kt
PROSODY_SENTENCES = [
    {
        "id": "ship_or_sheep_question",
        "text": "Is that a ship, or a sheep?",
        "voice": "tutor",
    },
    {
        "id": "pen_and_pan_statement",
        "text": "He bought a pen, and a pan.",
        "voice": "tutor",
    },
    {
        "id": "full_you_fool",
        "text": "Are you full, you fool?",
        "voice": "tutor",
    },
]

# 4 Meaning Trap Stories x 4 audio clips each = 16 clips from MeaningTrapCatalog.kt
MEANING_TRAP_CLIPS = [
    # 1. sheep_vs_ship (Old Farmer Giles)
    {
        "id": "sheep_vs_ship_prompt",
        "text": "Welcome to Giles' Farm! What gentle creature would you like to adopt today?",
        "voice": "farmer_giles",
    },
    {
        "id": "sheep_vs_ship_phrase",
        "text": "Can I have that sheep, please?",
        "voice": "tutor",
    },
    {
        "id": "sheep_vs_ship_intended",
        "text": "Splendid choice! Little Barnaby here will graze happily in your meadow.",
        "voice": "farmer_giles",
    },
    {
        "id": "sheep_vs_ship_trap",
        "text": "A SHIP?! Good heavens, where am I going to dock an ocean freighter on dry pasture?!",
        "voice": "farmer_giles",
    },
    # 2. pen_vs_pan (Strict Professor Smith)
    {
        "id": "pen_vs_pan_prompt",
        "text": "The exam starts in two minutes. All students must have an ink utensil ready.",
        "voice": "professor_smith",
    },
    {
        "id": "pen_vs_pan_phrase",
        "text": "Excuse me, could you lend me a pen?",
        "voice": "tutor",
    },
    {
        "id": "pen_vs_pan_intended",
        "text": "Certainly, here is a black ballpoint pen. Best of luck on your linguistics paper!",
        "voice": "professor_smith",
    },
    {
        "id": "pen_vs_pan_trap",
        "text": "A PAN?! Why on earth would you need a heavy cast-iron skillet for a written exam?!",
        "voice": "professor_smith",
    },
    # 3. heart_vs_hut (Cupid's Florist)
    {
        "id": "heart_vs_hut_prompt",
        "text": "What romantic message would you like engraved across the gold pendant?",
        "voice": "cupids_florist",
    },
    {
        "id": "heart_vs_hut_phrase",
        "text": "You hold the key to my heart.",
        "voice": "tutor",
    },
    {
        "id": "heart_vs_hut_intended",
        "text": "How utterly poetic! 'Key to my heart' is engraved in fine italic script.",
        "voice": "cupids_florist",
    },
    {
        "id": "heart_vs_hut_trap",
        "text": "'Key to my HUT'?! Are you locking your sweetheart into a wooden shed in the woods?!",
        "voice": "cupids_florist",
    },
    # 4. fool_vs_full (The Emperor's Butler)
    {
        "id": "fool_vs_full_prompt",
        "text": "Would your lordship care for another roasted boar tenderloin?",
        "voice": "emperors_butler",
    },
    {
        "id": "fool_vs_full_phrase",
        "text": "No thank you, I am completely full.",
        "voice": "tutor",
    },
    {
        "id": "fool_vs_full_intended",
        "text": "Very good, my lord. We shall bring the digestifs shortly.",
        "voice": "emperors_butler",
    },
    {
        "id": "fool_vs_full_trap",
        "text": "'You are completely FOOL'?! Did you just insult his Majesty's court jester?!",
        "voice": "emperors_butler",
    },
]


def get_api_key() -> str:
    for env_name in ("ELEVENLABS", "ELEVENLABS_API_KEY"):
        val = os.environ.get(env_name, "").strip()
        if val:
            return val

    # Fall back to querying Doppler CLI directly
    for project_args in (
        ["doppler", "secrets", "get", "ELEVENLABS", "--plain", "--project", "minimal-pairs", "--config", "dev"],
        ["doppler", "secrets", "get", "ELEVENLABS", "--plain"],
    ):
        try:
            key = subprocess.check_output(project_args, stderr=subprocess.DEVNULL).decode("utf-8").strip()
            if key:
                return key
        except Exception:
            pass

    raise RuntimeError(
        "Could not retrieve ElevenLabs API key from environment (`ELEVENLABS`) or Doppler (`minimal-pairs`/`dev`)."
    )


def wrap_pcm_to_wav(pcm_bytes: bytes) -> bytes:
    """Wraps raw 16kHz 16-bit mono signed little-endian PCM bytes in a standard 44-byte RIFF WAVE header."""
    byte_rate = SAMPLE_RATE * CHANNELS * (BITS_PER_SAMPLE // 8)
    block_align = CHANNELS * (BITS_PER_SAMPLE // 8)
    data_size = len(pcm_bytes)
    if not data_size or data_size % 2:
        raise ValueError("Expected nonempty 16-bit PCM audio")
    riff_size = 36 + data_size

    header = struct.pack(
        "<4sI4s4sIHHIIHH4sI",
        b"RIFF",
        riff_size,
        b"WAVE",
        b"fmt ",
        16,  # Subchunk1Size for PCM
        1,   # AudioFormat 1 = PCM
        CHANNELS,
        SAMPLE_RATE,
        byte_rate,
        block_align,
        BITS_PER_SAMPLE,
        b"data",
        data_size,
    )
    return header + pcm_bytes


def synthesize_pcm16k(
    api_key: str,
    voice_id: str,
    text: str,
    model_id: str = "eleven_multilingual_v2",
    stability: float = 0.65,
    similarity_boost: float = 0.80,
    style: float = 0.0,
) -> bytes:
    url = f"https://api.elevenlabs.io/v1/text-to-speech/{voice_id}?output_format=pcm_16000"
    payload = {
        "text": text,
        "model_id": model_id,
        "voice_settings": {
            "stability": stability,
            "similarity_boost": similarity_boost,
            "style": style,
            "use_speaker_boost": True,
        },
    }
    cmd = [
        "curl",
        "-sS",
        "--fail-with-body",
        "-X",
        "POST",
        url,
        "-H",
        f"xi-api-key: {api_key}",
        "-H",
        "Content-Type: application/json",
        "-d",
        json.dumps(payload),
    ]
    res = subprocess.run(cmd, capture_output=True)
    if res.returncode != 0:
        err_msg = res.stderr.decode("utf-8", errors="replace") + " " + res.stdout.decode("utf-8", errors="replace")
        raise RuntimeError(f"ElevenLabs API error for '{text}': {err_msg}")
    return res.stdout


def main():
    parser = argparse.ArgumentParser(description="Generate ElevenLabs WAV audio files for MinimalPairs.")
    parser.add_argument("--force", action="store_true", help="Overwrite existing WAV files.")
    parser.add_argument("--words-only", action="store_true", help="Generate only minimal-pair words, leaving other audio untouched.")
    args = parser.parse_args()

    api_key = get_api_key()
    print("Loaded ElevenLabs API key.")

    words_dir = OUTPUT_BASE / "words"
    prosody_dir = OUTPUT_BASE / "prosody"
    meaning_trap_dir = OUTPUT_BASE / "meaning_trap"

    for d in ((words_dir,) if args.words_only else (words_dir, prosody_dir, meaning_trap_dir)):
        d.mkdir(parents=True, exist_ok=True)

    generated = 0
    skipped = 0

    # 1. Generate practice words, preserving existing recordings unless --force is set.
    print(f"\n--- Generating {len(MINIMAL_PAIR_WORDS)} Minimal Pair Words ---")
    tutor_voice_id = VOICES["tutor"]["voice_id"]
    for item in MINIMAL_PAIR_WORDS:
        word = item["word"]
        ipa = item["ipa"]
        out_path = words_dir / f"{word}.wav"
        if out_path.exists() and not args.force and out_path.stat().st_size > 100:
            print(f"  [skip] words/{word}.wav")
            skipped += 1
            continue

        # Flash v2 supports SSML phoneme tags; the multilingual v2.5 model does not.
        cmu = item["cmu"]
        ssml_text = f'<phoneme alphabet="cmu-arpabet" ph="{cmu}">{word}</phoneme>.'
        print(f"  [gen]  words/{word}.wav  /{ipa}/ ...")
        pcm_bytes = synthesize_pcm16k(
            api_key=api_key,
            voice_id=tutor_voice_id,
            text=ssml_text,
            model_id="eleven_flash_v2",
            stability=0.75,
            similarity_boost=0.85,
        )
        wav_bytes = wrap_pcm_to_wav(pcm_bytes)
        out_path.write_bytes(wav_bytes)
        generated += 1
        time.sleep(0.08)

    if args.words_only:
        print(f"\nDone! Generated: {generated}, Skipped: {skipped}, Total files: {generated + skipped}")
        print(f"Output directory: {words_dir}")
        return

    # 2. Generate 3 Prosody Sentences
    print(f"\n--- Generating {len(PROSODY_SENTENCES)} Prosody Sentences ---")
    for item in PROSODY_SENTENCES:
        clip_id = item["id"]
        text = item["text"]
        voice_id = VOICES[item["voice"]]["voice_id"]
        out_path = prosody_dir / f"{clip_id}.wav"
        if out_path.exists() and not args.force and out_path.stat().st_size > 100:
            print(f"  [skip] prosody/{clip_id}.wav")
            skipped += 1
            continue

        print(f"  [gen]  prosody/{clip_id}.wav  (\"{text}\") ...")
        pcm_bytes = synthesize_pcm16k(
            api_key=api_key,
            voice_id=voice_id,
            text=text,
            model_id="eleven_multilingual_v2",
            stability=0.50,
            similarity_boost=0.80,
            style=0.25,
        )
        wav_bytes = wrap_pcm_to_wav(pcm_bytes)
        out_path.write_bytes(wav_bytes)
        generated += 1
        time.sleep(0.08)

    # 3. Generate 16 Meaning Trap Clips
    print(f"\n--- Generating {len(MEANING_TRAP_CLIPS)} Meaning Trap Dialogue & Phrase Clips ---")
    for item in MEANING_TRAP_CLIPS:
        clip_id = item["id"]
        text = item["text"]
        voice_key = item["voice"]
        voice_id = VOICES[voice_key]["voice_id"]
        voice_label = VOICES[voice_key]["name"]
        out_path = meaning_trap_dir / f"{clip_id}.wav"
        if out_path.exists() and not args.force and out_path.stat().st_size > 100:
            print(f"  [skip] meaning_trap/{clip_id}.wav")
            skipped += 1
            continue

        is_npc = voice_key != "tutor"
        print(f"  [gen]  meaning_trap/{clip_id}.wav  [{voice_label}] ...")
        pcm_bytes = synthesize_pcm16k(
            api_key=api_key,
            voice_id=voice_id,
            text=text,
            model_id="eleven_multilingual_v2",
            stability=0.45 if is_npc else 0.65,
            similarity_boost=0.80,
            style=0.35 if is_npc else 0.10,
        )
        wav_bytes = wrap_pcm_to_wav(pcm_bytes)
        out_path.write_bytes(wav_bytes)
        generated += 1
        time.sleep(0.08)

    print(f"\nDone! Generated: {generated}, Skipped: {skipped}, Total files: {generated + skipped}")
    print(f"Output directory: {OUTPUT_BASE}")


if __name__ == "__main__":
    main()
