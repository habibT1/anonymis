"""Normalisation de texte qui conserve les positions (1 caractère → 1 caractère)."""

import re
import unicodedata

_TITLES = re.compile(r"^(m\.|mme|mlle|mr|mrs|ms|dr|me|pr)\s+", re.IGNORECASE)


def fold_char(c: str) -> str:
    """Minuscule sans accent, en gardant exactement 1 caractère."""
    base = "".join(ch for ch in unicodedata.normalize("NFD", c) if not unicodedata.combining(ch))
    return base.lower() if len(base) == 1 else c.lower()


def fold(text: str) -> str:
    """« Éloïse DUPONT » → « eloise dupont », même longueur que l'entrée."""
    return "".join(fold_char(c) for c in text)


def canonical(value: str, entity_type: str = "") -> str:
    v = re.sub(r"\s+", " ", fold(value)).strip()
    if entity_type == "PERSONNE":
        v = _TITLES.sub("", v)
    if entity_type in {"TELEPHONE", "NAS", "NIR", "IBAN", "CARTE_BANCAIRE", "CODE_POSTAL"}:
        v = re.sub(r"[\s.\-()+]", "", v)
    return v


def shannon_entropy(s: str) -> float:
    import math
    from collections import Counter

    if not s:
        return 0.0
    n = len(s)
    return -sum(c / n * math.log2(c / n) for c in Counter(s).values())
