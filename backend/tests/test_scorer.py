import io
import wave
import pytest
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)


def generate_test_wav(duration_sec=0.2, sample_rate=16000) -> bytes:
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(sample_rate)
        num_frames = int(sample_rate * duration_sec)
        # 16-bit sine wave at 440 Hz
        raw = bytearray()
        import math

        for i in range(num_frames):
            sample = int(10000 * math.sin(2 * math.pi * 440 * i / sample_rate))
            raw.extend(sample.to_bytes(2, byteorder="little", signed=True))
        wav.writeframes(raw)
    return buffer.getvalue()


def test_health_check():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "phoneme-scorer"}


def test_score_phoneme_valid_audio():
    wav_bytes = generate_test_wav(duration_sec=0.25)
    files = {"audio_file": ("test.wav", wav_bytes, "audio/wav")}
    data = {
        "target_transcript": "sheep",
        "expected_ipa": "ʃ iː p",
    }
    response = client.post("/v1/score/phoneme", data=data, files=files)
    assert response.status_code == 200
    body = response.json()
    assert "overall_score" in body
    assert "phonemes" in body
    assert len(body["phonemes"]) == 3
    assert body["phonemes"][1]["phoneme"] == "iː"
    assert 0.0 <= body["overall_score"] <= 100.0


def test_score_phoneme_invalid_audio():
    files = {"audio_file": ("bad.wav", b"not-a-wav-file", "audio/wav")}
    data = {
        "target_transcript": "ship",
        "expected_ipa": "ʃ ɪ p",
    }
    response = client.post("/v1/score/phoneme", data=data, files=files)
    assert response.status_code == 400
