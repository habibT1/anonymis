"""privacy-engine : service local de détection des données sensibles et de traitement PDF.

N'écoute que sur 127.0.0.1. Ne journalise jamais le contenu des documents ni les valeurs détectées.
"""

import json
import logging

from fastapi import FastAPI, File, Form, HTTPException, Response, UploadFile
from pydantic import BaseModel, Field

from engine import detector, pdf
from engine.recognizers import ner
from engine.models import Level

logging.basicConfig(level=logging.INFO)

app = FastAPI(title="Anonymis privacy-engine", version="0.1.0")

MAX_PDF_BYTES = 30 * 1024 * 1024


class WatchItem(BaseModel):
    term: str
    category: str = "TERME_SENSIBLE"


class DetectRequest(BaseModel):
    text: str
    level: Level = Level.STANDARD
    watchlist: list[WatchItem] = Field(default_factory=list)
    allowlist: list[str] = Field(default_factory=list)


@app.get("/health")
def health():
    return {"status": "ok", "ner": ner.available()}


@app.post("/detect")
def detect(req: DetectRequest):
    entities = detector.detect(req.text, req.level, [w.model_dump() for w in req.watchlist], req.allowlist)
    return {"entities": [e.to_dict() for e in entities], "summary": detector.summary(entities)}


@app.post("/extract")
async def extract(file: UploadFile = File(...)):
    data = await file.read()
    if len(data) > MAX_PDF_BYTES:
        raise HTTPException(413, "PDF trop volumineux (max 30 Mo)")
    if not data.startswith(b"%PDF"):
        raise HTTPException(415, "Le fichier n'est pas un PDF")
    try:
        return pdf.extract(data)
    except Exception:
        raise HTTPException(422, "PDF illisible ou corrompu")


async def _read_pdf(file: UploadFile) -> bytes:
    data = await file.read()
    if len(data) > MAX_PDF_BYTES:
        raise HTTPException(413, "PDF trop volumineux (max 30 Mo)")
    if not data.startswith(b"%PDF"):
        raise HTTPException(415, "Le fichier n'est pas un PDF")
    return data


@app.post("/redact")
async def redact(file: UploadFile = File(...), spans: str = Form(...)):
    """spans : JSON [{start, end, label}] dans les positions du texte renvoyé par /extract."""
    data = await _read_pdf(file)
    try:
        parsed = json.loads(spans)
    except json.JSONDecodeError:
        raise HTTPException(400, "spans invalide")
    try:
        out = pdf.redact(data, parsed)
    except Exception:
        raise HTTPException(422, "PDF illisible ou corrompu")
    return Response(out, media_type="application/pdf")


class RenderRequest(BaseModel):
    title: str = "Anonymis"
    text: str


@app.post("/render")
def render(req: RenderRequest):
    return Response(pdf.render(req.title, req.text), media_type="application/pdf")
