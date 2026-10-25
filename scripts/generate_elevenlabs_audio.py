#!/usr/bin/env python3
"""
Generates 16kHz 16-bit mono PCM WAV audio assets for MinimalPairs using ElevenLabs TTS.
Fetches the ElevenLabs API key via Doppler (secret `ELEVENLABS` in project `minimal-pairs`, config `dev`,
or from `ELEVENLABS` / `ELEVENLABS_API_KEY` environment variables).

Usage:
    doppler run --project minimal-pairs --config dev -- python3 scripts/generate_elevenlabs_audio.py
    # or directly (auto-invokes doppler if env var is not set):
    python3 scripts/generate_elevenlabs_audio.py [--force]
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

# 58 Unique Minimal Pair Words across all 6 contrasts in VowelContrastsCatalog
# Words with explicit IPA overrides use `eleven_flash_v2_5` with SSML <phoneme> tags to ensure
# crisp phonetic contrast (e.g. preventing the cot/caught merger in American English).
MINIMAL_PAIR_WORDS = [
    # 1. i_vs_I (/iː/ vs /ɪ/)
    {"word": "sheep", "ipa": "ʃiːp"},
    {"word": "ship", "ipa": "ʃɪp"},
    {"word": "feet", "ipa": "fiːt"},
    {"word": "fit", "ipa": "fɪt"},
    {"word": "seat", "ipa": "siːt"},
    {"word": "sit", "ipa": "sɪt"},
    {"word": "leak", "ipa": "liːk"},
    {"word": "lick", "ipa": "lɪk"},
    {"word": "cheeks", "ipa": "tʃiːks"},
    {"word": "chicks", "ipa": "tʃɪks"},
    # 2. ae_vs_e (/æ/ vs /e/)
    {"word": "bad", "ipa": "bæd"},
    {"word": "bed", "ipa": "bɛd"},
    {"word": "man", "ipa": "mæn"},
    {"word": "men", "ipa": "mɛn"},
    {"word": "pan", "ipa": "pæn"},
    {"word": "pen", "ipa": "pɛn"},
    {"word": "sad", "ipa": "sæd"},
    {"word": "said", "ipa": "sɛd"},
    {"word": "cattle", "ipa": "ˈkætəl"},
    {"word": "kettle", "ipa": "ˈkɛtəl"},
    # 3. a_vs_uh (/ɑː/ vs /ʌ/)
    {"word": "heart", "ipa": "hɑːɹt"},
    {"word": "hut", "ipa": "hʌt"},
    {"word": "calm", "ipa": "kɑːm"},
    {"word": "come", "ipa": "kʌm"},
    {"word": "dark", "ipa": "dɑːɹk"},
    {"word": "duck", "ipa": "dʌk"},
    {"word": "march", "ipa": "mɑːɹtʃ"},
    {"word": "much", "ipa": "mʌtʃ"},
    {"word": "barn", "ipa": "bɑːɹn"},
    {"word": "bun", "ipa": "bʌn"},
    # 4. u_vs_uu (/ʊ/ vs /uː/)
    {"word": "full", "ipa": "fʊl"},
    {"word": "fool", "ipa": "fuːl"},
    {"word": "pull", "ipa": "pʊl"},
    {"word": "pool", "ipa": "puːl"},
    {"word": "look", "ipa": "lʊk"},
    {"word": "luke", "ipa": "luːk"},
    {"word": "soot", "ipa": "sʊt"},
    {"word": "suit", "ipa": "suːt"},
    {"word": "stood", "ipa": "stʊd"},
    {"word": "stewed", "ipa": "stuːd"},
    # 5. o_vs_or (/ɑː/ vs /ɔː/)
    {"word": "cot", "ipa": "kɑːt"},
    {"word": "caught", "ipa": "kɔːt"},
    {"word": "shot", "ipa": "ʃɑːt"},
    {"word": "short", "ipa": "ʃɔːɹt"},
    {"word": "pot", "ipa": "pɑːt"},
    {"word": "port", "ipa": "pɔːɹt"},
    {"word": "spot", "ipa": "spɑːt"},
    {"word": "sport", "ipa": "spɔːɹt"},
    {"word": "fox", "ipa": "fɑːks"},
    {"word": "forks", "ipa": "fɔːɹks"},
    # 6. e_vs_I (/e/ vs /ɪ/) - ("pen" and "sit" already listed above)
    {"word": "pin", "ipa": "pɪn"},
    {"word": "ten", "ipa": "tɛn"},
    {"word": "tin", "ipa": "tɪn"},
    {"word": "bet", "ipa": "bɛt"},
    {"word": "bit", "ipa": "bɪt"},
    {"word": "set", "ipa": "sɛt"},
    {"word": "dead", "ipa": "dɛd"},
    {"word": "did", "ipa": "dɪd"},
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
    args = parser.parse_args()

    api_key = get_api_key()
    print(f"Loaded ElevenLabs API key via Doppler (length={len(api_key)}).")

    words_dir = OUTPUT_BASE / "words"
    prosody_dir = OUTPUT_BASE / "prosody"
    meaning_trap_dir = OUTPUT_BASE / "meaning_trap"

    for d in (words_dir, prosody_dir, meaning_trap_dir):
        d.mkdir(parents=True, exist_ok=True)

    generated = 0
    skipped = 0

    # 1. Generate 58 Minimal Pair Words
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

        # Use SSML phoneme tag with eleven_flash_v2_5 for precise IPA articulation
        ssml_text = f'<phoneme alphabet="ipa" ph="{ipa}">{word}</phoneme>.'
        print(f"  [gen]  words/{word}.wav  /{ipa}/ ...")
        pcm_bytes = synthesize_pcm16k(
            api_key=api_key,
            voice_id=tutor_voice_id,
            text=ssml_text,
            model_id="eleven_flash_v2_5",
            stability=0.75,
            similarity_boost=0.85,
        )
        wav_bytes = wrap_pcm_to_wav(pcm_bytes)
        out_path.write_bytes(wav_bytes)
        generated += 1
        time.sleep(0.08)

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
