import io
import os
from pathlib import Path
import wave
from typing import List, Literal

import numpy as np
from fastapi import FastAPI, File, Form, UploadFile, HTTPException
from pydantic import BaseModel, Field
from starlette.concurrency import run_in_threadpool

from .ctc_scorer import InvalidTarget, ModelUnavailable, RecordingQualityError, get_model, model_is_loaded

app = FastAPI(
    title="Phoneme Scorer Service",
    version="2.3.0",
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
    target_phoneme_index: int | None = None
    alternative_phoneme: str | None = None
    contrast_evidence: float | None = Field(None, ge=0, le=1)
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
    # Whole-clip RMS falls as the user waits before/after speaking. Only reject
    # effectively silent input here; word-level quality checks follow endpointing.
    if float(np.max(np.abs(samples - samples.mean()))) < 0.001:
        raise HTTPException(status_code=400, detail="No audible speech detected")
    return samples


@app.post("/v1/score/phoneme", response_model=PhonemeScoringResponse)
async def score_phoneme(
    target_transcript: str = Form(..., max_length=200),
    expected_ipa: str = Form(..., max_length=500),
    audio_file: UploadFile = File(...),
    target_phoneme_index: int | None = Form(None, ge=0),
    utterance_kind: Literal["word", "phrase"] = Form("word"),
    alternative_phoneme: str | None = Form(None, max_length=16),
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
    if target_phoneme_index is not None and target_phoneme_index >= len(phonemes):
        raise HTTPException(status_code=400, detail="Target phoneme index is outside the word transcript")
    if utterance_kind == "phrase" and target_phoneme_index is None:
        raise HTTPException(status_code=400, detail="Phrase scoring requires a selected phoneme index")
    if alternative_phoneme is not None and (utterance_kind != "phrase" or
            target_phoneme_index is None or not alternative_phoneme.strip() or
            len(alternative_phoneme.split()) != 1 or alternative_phoneme == phonemes[target_phoneme_index]):
        raise HTTPException(status_code=400, detail="Provide one distinct alternate phoneme for a selected phrase contrast")
    try:
        model = await run_in_threadpool(get_model)
        if target_phoneme_index is not None:
            if utterance_kind == "phrase":
                options = {"utterance_kind": "phrase"}
                if alternative_phoneme is not None:
                    options["alternative_phoneme"] = alternative_phoneme
                scores = await run_in_threadpool(model.score, samples, phonemes, target_phoneme_index, **options)
            else:
                scores = await run_in_threadpool(model.score, samples, phonemes, target_phoneme_index)
            return PhonemeScoringResponse(
                overall_score=scores[target_phoneme_index]["gop_score"],
                phonemes=scores,
                model_id=model.model_id,
                scoring_method="neural_ctc_phrase_contrast" if alternative_phoneme is not None else
                    "neural_ctc_phrase_phoneme" if utterance_kind == "phrase" else "neural_ctc_phoneme",
                target_phoneme_index=target_phoneme_index,
                alternative_phoneme=alternative_phoneme,
                contrast_evidence=scores[target_phoneme_index].get("contrast_evidence"),
                feedback="Experimental confidence between the selected vowel and its alternate within the phrase; not calibrated pronunciation accuracy."
                    if alternative_phoneme is not None else
                    f"Experimental confidence for the selected phoneme aligned within the {utterance_kind}; not calibrated pronunciation accuracy.",
            )
        scores = await run_in_threadpool(model.score, samples, phonemes)
        return PhonemeScoringResponse(
            overall_score=sum(score["gop_score"] for score in scores) / len(scores),
            phonemes=scores,
            model_id=model.model_id,
        )
    except RecordingQualityError as error:
        raise HTTPException(status_code=400, detail={"code": error.code, "message": str(error)}) from error
    except InvalidTarget as error:
        raise HTTPException(status_code=400, detail=str(error)) from error
    except ModelUnavailable as error:
        raise HTTPException(status_code=503, detail=str(error)) from error
    except Exception as error:
        raise HTTPException(status_code=503, detail="The phoneme model could not evaluate this recording") from error
