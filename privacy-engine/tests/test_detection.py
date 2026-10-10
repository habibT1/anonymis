"""Tests sur des données entièrement fictives."""

import pymupdf
import pytest
from fastapi.testclient import TestClient

from app import app
from engine import detector
from engine.models import Level
from engine.recognizers import ner, pii

client = TestClient(app)
needs_ner = pytest.mark.skipif(not ner.available(), reason="modèle spaCy absent")


def types(text, **kw):
    return [(e.type, e.value) for e in detector.detect(text, **kw)]


# ---------- Secrets ----------

def test_env_file_keeps_names_masks_values():
    env = (
        "OPENAI_API_KEY=sk-proj-FAKEfakeFAKEfake1234567890abcd\n"
        "DB_PASSWORD=Sup3rS3cret!\n"
        "DEBUG=true\n"
        "PORT=8080\n"
    )
    found = detector.detect(env)
    values = {e.value for e in found}
    assert "sk-proj-FAKEfakeFAKEfake1234567890abcd" in values
    assert "Sup3rS3cret!" in values
    assert not any("OPENAI_API_KEY" in v or "DB_PASSWORD" in v for v in values)
    assert "true" not in values and "8080" not in values


def test_key_on_same_line_as_other_secret_keeps_variable_name():
    found = types("DB_PASSWORD=Hunter2024! OPENAI_API_KEY=sk-proj-FAKEfakeFAKEfake1234567890")
    assert ("CLE_API", "sk-proj-FAKEfakeFAKEfake1234567890") in found
    assert not any("OPENAI_API_KEY" in v for _, v in found)


@pytest.mark.parametrize("key", [
    "AKIAIOSFODNN7EXAMPLE",
    "AIzaSyA-FAKEfakeFAKEfakeFAKEfake1234567",
    "ghp_FAKEfakeFAKEfakeFAKEfakeFAKEfake1234",
    "xoxb-123456789012-FAKEfakeFAKE",
    "sk_live_FAKEfakeFAKEfake1234",
])
def test_known_key_formats(key):
    assert ("CLE_API", key) in types(f"la clé est {key} ok")


def test_private_key_block_and_jwt():
    text = ('{"private_key": "-----BEGIN PRIVATE KEY-----\\nMIIEvFAKE\\n-----END PRIVATE KEY-----\\n"}\n'
            "Authorization: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0In0.FAKEsignature123")
    t = [x[0] for x in types(text)]
    assert "CLE_PRIVEE" in t and "JETON" in t


def test_connection_string_masks_credentials_and_host():
    found = types("url = postgres://admin:hunter22@db.acme.internal:5432/prod")
    assert ("CONNEXION", "admin:hunter22@db.acme.internal") in found


# ---------- Identifiants personnels ----------

def test_identifiers():
    text = ("Courriel : jean.tremblay@exemple.ca, tél. 514-555-0199, NAS 046 454 286, "
            "IBAN FR76 3000 6000 0112 3456 7890 189, carte 4111 1111 1111 1111, H2X 1Y4")
    t = {x[0] for x in types(text)}
    assert {"EMAIL", "TELEPHONE", "NAS", "IBAN", "CARTE_BANCAIRE", "CODE_POSTAL"} <= t


def test_invalid_checksums_are_ignored():
    assert not pii.luhn_ok("046 454 287")
    assert not pii.iban_ok("FR76 3000 6000 0112 3456 7890 188")
    assert "NAS" not in {x[0] for x in types("numéro 046 454 287")}


@pytest.mark.parametrize("text,date", [
    ("Mme Lavoie, née le 12 mars 1990, demeurant", "12 mars 1990"),
    ("Il est né le 03/04/1985 à Lyon", "03/04/1985"),
    ("Date de naissance : 1990-03-12", "1990-03-12"),
])
def test_birth_date(text, date):
    assert ("DATE_NAISSANCE", date) in types(text)


@needs_ner
def test_regex_beats_ner_on_overlap():
    text = "demeurant au 88 rue des Érables, Laval H7N 2K4, ci-après la Salariée."
    found = types(text)
    assert ("CODE_POSTAL", "H7N 2K4") in found
    assert not any("2K4" in v and t != "CODE_POSTAL" for t, v in found)


@needs_ner
def test_receipt_headers_are_not_names():
    receipt = ("REÇU DE PAIEMENT\nClient : Habib Traore\nDate : 2026-10-01\n"
               "Description  Qté  Prix unitaire  Montant\nTotal  34,48 $\n"
               "Le prix total inclut les taxes, à la date d'achat.")
    found = detector.detect(receipt)
    people = [e for e in found if e.type == "PERSONNE"]
    assert all("\n" not in e.value for e in found)
    assert {e.value for e in people} <= {"Habib Traore"}
    assert not any(e.value.lower() in {"date", "prix", "total"} for e in found)


@needs_ner
def test_delivery_receipt_addresses_and_caps_names():
    receipt = ("Merci pour votre commande, JEAN !\n"
               "Adresse de ramassage : rue de la Montagne O, Montréal, QC H3G\n1Z8, CA\n"
               "Adresse de livraison : 1234 rue saint-urbain, Montréal, QC H2X 2N1, CA\n"
               "Client : JEAN TREMBLAY\nTotal : 94,52 $")
    found = types(receipt)
    values = " | ".join(v for _, v in found)
    assert "1234 rue saint-urbain" in values
    assert "rue de la Montagne O" in values
    assert ("PERSONNE", "JEAN TREMBLAY") in found
    assert any(t == "PERSONNE" and v == "JEAN" for t, v in found)


def test_tokens_are_not_detected_again():
    # LeakGuard relance la détection sur le texte anonymisé : un jeton ne doit pas être pris pour une donnée.
    assert types("Adresse de livraison : [ADRESSE_2], [LIEU_1] [CODE_POSTAL_1]") == []


def test_address():
    assert ("ADRESSE", "1234 rue Sainte-Catherine") in types("Il habite au 1234 rue Sainte-Catherine, Montréal.")


# ---------- Dictionnaire ----------

def test_watchlist_and_allowlist():
    text = "Le projet faucon sera présenté à Banque Laurentienne."
    found = types(text, watchlist=[{"term": "Projet Faucon", "category": "PROJET"},
                                   {"term": "banque laurentienne", "category": "CLIENT"}])
    assert ("PROJET", "projet faucon") in found
    assert ("CLIENT", "Banque Laurentienne") in found
    assert types("Le projet faucon", watchlist=[{"term": "Projet Faucon"}], allowlist=["projet faucon"]) == []


# ---------- NER ----------

@needs_ner
def test_person_variants_share_canonical():
    text = ("Contrat entre Jean Dupont et la société Acme. "
            "M. Dupont s'engage à donner un préavis. Dupont signe le contrat.")
    people = [e for e in detector.detect(text) if e.type == "PERSONNE"]
    assert len(people) >= 2
    assert {e.canonical for e in people} == {"jean dupont"}


@needs_ner
def test_strict_level_adds_organisations():
    text = "Marie Gagnon travaille chez Desjardins à Montréal."
    std = {e.type for e in detector.detect(text)}
    strict = {e.type for e in detector.detect(text, level=Level.STRICT)}
    assert "ORGANISATION" not in std
    assert {"LIEU"} <= strict or {"ORGANISATION"} <= strict


# ---------- API ----------

def test_api_detect():
    r = client.post("/detect", json={"text": "Écrire à alice@exemple.com, token=ghp_FAKEfakeFAKEfakeFAKEfakeFAKEfake1234"})
    assert r.status_code == 200
    body = r.json()
    assert body["summary"]["secrets"] == 1
    assert body["summary"]["by_type"]["EMAIL"] == 1


def test_api_extract_offsets_match_text():
    doc = pymupdf.open()
    page = doc.new_page()
    page.insert_text((72, 72), "Locataire : Jean Dupont")
    page.insert_text((72, 100), "Courriel : jean.dupont@exemple.ca")
    r = client.post("/extract", files={"file": ("bail.pdf", doc.tobytes(), "application/pdf")})
    assert r.status_code == 200
    body = r.json()
    assert "jean.dupont@exemple.ca" in body["text"]
    for w in body["pages"][0]["words"]:
        assert body["text"][w["start"]:w["end"]] == w["text"]


def test_api_extract_rejects_non_pdf():
    r = client.post("/extract", files={"file": ("x.pdf", b"hello", "application/pdf")})
    assert r.status_code == 415


# ---------- Export PDF ----------

def _sample_pdf() -> bytes:
    doc = pymupdf.open()
    page = doc.new_page()
    page.insert_text((72, 72), "Locataire : Jean Dupont")
    page.insert_text((72, 100), "Courriel : jean.dupont@exemple.ca")
    doc.set_metadata({"author": "Jean Dupont"})
    return doc.tobytes()


def test_redact_removes_text_and_metadata():
    data = _sample_pdf()
    text = client.post("/extract", files={"file": ("a.pdf", data, "application/pdf")}).json()["text"]
    spans = []
    for value, label in [("Jean Dupont", "[PERSONNE_1]"), ("jean.dupont@exemple.ca", "[EMAIL_1]")]:
        i = text.index(value)
        spans.append({"start": i, "end": i + len(value), "label": label})
    import json as _json
    r = client.post("/redact", files={"file": ("a.pdf", data, "application/pdf")},
                    data={"spans": _json.dumps(spans)})
    assert r.status_code == 200
    out = pymupdf.open(stream=r.content, filetype="pdf")
    page_text = out[0].get_text()
    assert "Dupont" not in page_text and "exemple.ca" not in page_text
    assert "Locataire" in page_text
    assert "[PERSONNE_1]" in page_text
    assert not (out.metadata or {}).get("author")


def test_render_multi_page():
    r = client.post("/render", json={"title": "Réponse", "text": "\n".join(f"Ligne {i} pour Sophie Lavoie" for i in range(200))})
    assert r.status_code == 200
    out = pymupdf.open(stream=r.content, filetype="pdf")
    assert out.page_count >= 2
    assert "Sophie Lavoie" in out[0].get_text()
