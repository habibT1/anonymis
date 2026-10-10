"""Reconnaissance d'entités nommées (personnes, organisations, lieux) avec spaCy, en local."""

import logging
import os
import re

from engine.models import Category, Entity
from engine.text import fold

log = logging.getLogger(__name__)

_LABELS = {"PER": "PERSONNE", "ORG": "ORGANISATION", "LOC": "LIEU"}
_nlp = None
_loaded = False


def _model():
    global _nlp, _loaded
    if not _loaded:
        _loaded = True
        name = os.getenv("SPACY_MODEL", "fr_core_news_md")
        try:
            import spacy

            _nlp = spacy.load(name, disable=["parser", "lemmatizer", "tagger", "morphologizer", "attribute_ruler"])
            log.info("Modèle spaCy chargé : %s", name)
        except Exception as e:  # modèle absent : on continue avec les regex seules
            log.warning("NER désactivée (%s) : %s", name, e)
    return _nlp


def lowercase_words(text: str) -> set[str]:
    """Mots écrits entièrement en minuscules dans le texte (donc des noms communs)."""
    return {fold(w) for w in re.findall(r"[^\W\d_]+", text) if w.islower()}


_EDGE = " \t\n.,;:!?()[]«»\"'’"
_CAPS_WORD = re.compile(r"\b[^\W\d_]{2,}\b")


def _entities(nlp, text: str):
    """Entités du texte, plus les noms écrits en MAJUSCULES (« JEAN TREMBLAY ») que le modèle rate :
    on les cherche dans une copie où ces mots sont en « Jean Tremblay » (même longueur, mêmes positions)."""
    yield from nlp(text).ents
    if not any(w.isupper() for w in _CAPS_WORD.findall(text)):
        return
    titled = _CAPS_WORD.sub(lambda m: m.group(0).capitalize() if m.group(0).isupper() else m.group(0), text)
    if len(titled) != len(text):
        return
    for ent in nlp(titled).ents:
        original = text[ent.start_char:ent.end_char]
        # Seulement les noms de personne d'au moins deux mots, entièrement en majuscules dans l'original
        # (« JEAN TREMBLAY »). Un mot seul est souvent un sigle (« NAS », « TPS ») ; « JEAN » seul est
        # rattrapé ensuite comme variante du nom complet.
        words = _CAPS_WORD.findall(original.split("\n")[0])
        if ent.label_ == "PER" and len(words) >= 2 and all(w.isupper() for w in words):
            yield _Span(original, ent.label_, ent.start_char)


class _Span:
    """Même interface que les entités spaCy utilisées plus haut."""

    def __init__(self, text: str, label: str, start: int):
        self.text, self.label_, self.start_char = text, label, start


def available() -> bool:
    return _model() is not None


def detect(text: str) -> list[Entity]:
    nlp = _model()
    if nlp is None:
        return []
    found = []
    common_words = lowercase_words(text)
    for ent in _entities(nlp, text):
        etype = _LABELS.get(ent.label_)
        if not etype:
            continue
        # Une entité ne traverse jamais un saut de ligne (« Habib Traore\nDate » → « Habib Traore »)
        # et n'emporte pas la ponctuation autour (« JEAN ! » → « JEAN »).
        raw = ent.text.split("\n")[0] if not ent.text.lstrip().startswith("\n") else ent.text.lstrip()
        value = raw.strip(_EDGE)
        start = ent.start_char + ent.text.index(value) if value else ent.start_char
        if len(value) < 2 or not any(c.isupper() for c in value):
            continue
        if value.startswith("[") and value.endswith("]"):  # jeton déjà posé
            continue
        if any(c.isdigit() for c in value):
            continue
        # Un seul mot qui existe aussi en minuscules dans le texte est un nom commun (« Total », « Prix »).
        if " " not in value and fold(value) in common_words:
            continue
        # Priorité sous les regex validées : « Laval H7N » (faux positif NER) ne doit pas
        # l'emporter sur le code postal « H7N 2K4 » et laisser « 2K4 » en clair.
        found.append(Entity(etype, Category.PII, start, start + len(value), value, 0.7, "ner", priority=15))
    return found
