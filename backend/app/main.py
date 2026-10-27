import io
import os
from pathlib import Path
import wave
from typing import List

import numpy as np
from fastapi import FastAPI, File, Form, UploadFile, HTTPException
from pydantic import BaseModel, Field
from starlette.concurrency import run_in_threadpool

from .ctc_scorer import InvalidTarget, ModelUnavailable, get_model, model_is_loaded

app = FastAPI(
    title="Phoneme Scorer Service",
    version="2.0.0",
    description="Local phoneme CTC alignment and uncalibrated posterior confidence",
)


class PhonemeScore(BaseModel):
    phoneme: str
    gop_score: float = Field(..., ge=0, le=100, description="Aligned posterior confidence; legacy field name, not calibrated GOP")
    start_time: float = Field(..., ge=0, description="CTC token-emission start; not a validated phonetic boundary")
    end_time: float = Field(..., ge=0, description="CTC token-emission end; not a vowel-duration measurement")


class PhonemeScoringResponse(BaseModel):
    overall_score: float = Field(..., ge=0, le=100)
    phonemes: List[PhonemeScore]
    scoring_method: str = "neural_ctc"
    model_id: str
    feedback: str = "Experimental model confidence; not validated for isolated-vowel pronunciation coaching or calibrated as a pronunciation grade."


@app.get("/health")
def health_check():
    # Liveness is deliberately independent of loading a potentially large model.
    directory = os.environ.get("PHONEME_MODEL_DIR")
    model_status = "loaded" if model_is_loaded() else (
        "not_loaded" if directory and Path(directory).is_dir() else "unavailable")
    return {"status": "ok", "service": "phoneme-scorer", "model_status": model_status}


def decode_audio(audio_bytes):
    try:
        with wave.open(io.BytesIO(audio_bytes), "rb") as wav:
            if (wav.getnchannels(), wav.getsampwidth(), wav.getframerate(), wav.getcomptype()) != (1, 2, 16000, "NONE"):
                raise ValueError("Expected 16kHz, 16-bit, mono PCM WAV")
            frames = wav.getnframes()
            if not 1280 <= frames <= 480000:
                raise ValueError("Recording must be between 80ms and 30 seconds")
            raw = wav.readframes(frames)
            if len(raw) != frames * 2:
                raise ValueError("Truncated WAV audio")
    except (wave.Error, EOFError, ValueError) as error:
        raise HTTPException(status_code=400, detail=str(error) or "Invalid WAV audio") from error
    samples = np.frombuffer(raw, dtype="<i2").astype(np.float32) / 32768
    if float(np.sqrt(np.mean(samples ** 2))) < 0.001:
        raise HTTPException(status_code=400, detail="No audible speech detected")
    return samples


@app.post("/v1/score/phoneme", response_model=PhonemeScoringResponse)
async def score_phoneme(
    target_transcript: str = Form(..., max_length=200),
    expected_ipa: str = Form(..., max_length=500),
    audio_file: UploadFile = File(...),
):
    audio_bytes = await audio_file.read(1_000_001)
    if len(audio_bytes) > 1_000_000:
        raise HTTPException(status_code=413, detail="Audio upload exceeds 1 MB")
    samples = decode_audio(audio_bytes)
    phonemes = expected_ipa.strip().strip("/").split()
    if not target_transcript.strip() or not phonemes or len(phonemes) > 128:
        raise HTTPException(status_code=400, detail="Provide a transcript and space-separated IPA phonemes")
    if len(phonemes) == 1:
        raise HTTPException(status_code=400, detail="This model does not support isolated-phoneme scoring; use the acoustic estimate")
    try:
        model = await run_in_threadpool(get_model)
        scores = await run_in_threadpool(model.score, samples, phonemes)
        return PhonemeScoringResponse(
            overall_score=sum(score["gop_score"] for score in scores) / len(scores),
            phonemes=scores,
            model_id=model.model_id,
        )
    except InvalidTarget as error:
        raise HTTPException(status_code=400, detail=str(error)) from error
    except ModelUnavailable as error:
        raise HTTPException(status_code=503, detail=str(error)) from error
    except Exception as error:
        raise HTTPException(status_code=503, detail="The phoneme model could not evaluate this recording") from error
