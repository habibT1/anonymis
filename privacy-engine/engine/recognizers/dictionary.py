"""Liste de surveillance : noms métier que l'utilisateur veut toujours masquer."""

import re

from engine.models import Category, Entity
from engine.text import fold


def find_all(text: str, folded_text: str, term: str) -> list[tuple[int, int]]:
    """Occurrences d'un terme, insensible à la casse et aux accents, en mots entiers."""
    t = fold(term.strip())
    if not t:
        return []
    pattern = re.compile(r"(?<!\w)" + re.escape(t).replace(r"\ ", r"\s+") + r"(?!\w)")
    return [(m.start(), m.end()) for m in pattern.finditer(folded_text)]


def detect(text: str, watchlist: list[dict]) -> list[Entity]:
    folded = fold(text)
    found = []
    for item in watchlist:
        term = item.get("term", "")
        etype = (item.get("category") or "TERME_SENSIBLE").upper()
        for start, end in find_all(text, folded, term):
            found.append(Entity(etype, Category.BUSINESS, start, end, text[start:end], 1.0, "dictionnaire"))
    return found
