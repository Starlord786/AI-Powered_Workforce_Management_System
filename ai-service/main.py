import io
import json

import face_recognition
import numpy as np
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from PIL import Image, ImageOps

app = FastAPI(title="Workforce AI Service")

TOLERANCE = 0.5   # lower = stricter matching
MAX_SIZE = 800    # shrink big photos so detection stays fast


def read_face(data: bytes):
    try:
        img = ImageOps.exif_transpose(Image.open(io.BytesIO(data))).convert("RGB")
    except Exception:
        raise HTTPException(status_code=400, detail="Could not read the image")
    img.thumbnail((MAX_SIZE, MAX_SIZE))
    arr = np.array(img)

    locations = face_recognition.face_locations(arr)
    if len(locations) == 0:
        raise HTTPException(status_code=400, detail="No face found in the image")
    if len(locations) > 1:
        raise HTTPException(status_code=400, detail="More than one face found in the image")
    return face_recognition.face_encodings(arr, known_face_locations=locations)[0]


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/face/encode")
async def encode(file: UploadFile = File(...)):
    enc = read_face(await file.read())
    return {"encoding": enc.tolist()}


@app.post("/face/match")
async def match(file: UploadFile = File(...), known: str = Form(...)):
    try:
        people = json.loads(known)
    except ValueError:
        raise HTTPException(status_code=400, detail="'known' must be valid JSON")
    if not people:
        return {"match": False, "reason": "No registered faces yet"}

    enc = read_face(await file.read())
    known_enc = np.array([p["encoding"] for p in people])
    distances = face_recognition.face_distance(known_enc, enc)
    best = int(np.argmin(distances))

    if distances[best] <= TOLERANCE:
        return {
            "match": True,
            "employee_id": people[best]["employee_id"],
            "confidence": round(float(1 - distances[best]), 3),
        }
    return {"match": False, "reason": "Face not recognised"}
