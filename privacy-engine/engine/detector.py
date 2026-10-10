"""Combine les détecteurs, résout les chevauchements et relie les variantes d'une même entité."""

from engine.models import Category, Entity, Level
from engine.recognizers import dictionary, ner, pii, secrets
from engine.text import canonical, fold

# Types de la NER masqués selon le niveau.
_NER_TYPES = {
    Level.STANDARD: {"PERSONNE"},
    Level.STRICT: {"PERSONNE", "ORGANISATION", "LIEU"},
}
_PROPAGATED = {"PERSONNE", "ORGANISATION", "LIEU"}
_TITLES = {"mme", "mlle", "m.", "mr", "mrs", "dr", "me", "pr"}


def _resolve_overlaps(entities: list[Entity]) -> list[Entity]:
    """Garde la meilleure entité par zone : priorité, puis longueur, puis score."""
    ranked = sorted(entities, key=lambda e: (-e.priority, -(e.end - e.start), -e.score))
    kept: list[Entity] = []
    for e in ranked:
        if not any(e.overlaps(k) for k in kept):
            kept.append(e)
    return sorted(kept, key=lambda e: e.start)


def _propagate(text: str, entities: list[Entity]) -> list[Entity]:
    """La NER rate souvent les mentions répétées : on cherche chaque nom trouvé partout dans le texte.
    Le nom de famille seul (« Dupont ») hérite du canonical du nom complet (« jean dupont »)."""
    folded = fold(text)
    lowercase_words = ner.lowercase_words(text)
    extra: list[Entity] = []
    for e in entities:
        if e.type not in _PROPAGATED:
            continue
        variants = [e.value]
        words = e.value.split()
        # Le prénom ou le nom seul, sauf s'il sert aussi de mot courant dans le texte (« Date », « Rose »).
        if e.type == "PERSONNE" and len(words) >= 2:
            for w in (words[0], words[-1]):
                if len(w) >= 3 and fold(w) not in lowercase_words and fold(w) not in _TITLES:
                    variants.append(w)
        for v in variants:
            for start, end in dictionary.find_all(text, folded, v):
                if not text[start].isupper():  # « rose » ≠ « Rose »
                    continue
                extra.append(Entity(e.type, e.category, start, end, text[start:end], e.score - 0.05,
                                    "propagation", canonical=e.canonical))
    return extra


def _link_variants(entities: list[Entity]) -> None:
    """« Dupont » ou « M. Dupont » → même canonical que « Jean Dupont » s'il n'y a qu'un Dupont."""
    full_names = {e.canonical for e in entities if e.type == "PERSONNE" and " " in e.canonical}
    for e in entities:
        if e.type != "PERSONNE" or " " in e.canonical:
            continue
        matches = [f for f in full_names if e.canonical in f.split()]
        if len(matches) == 1:
            e.canonical = matches[0]


def detect(text: str, level: Level = Level.STANDARD,
           watchlist: list[dict] | None = None, allowlist: list[str] | None = None) -> list[Entity]:
    entities = secrets.detect(text) + pii.detect(text)
    entities += [e for e in ner.detect(text) if e.type in _NER_TYPES[level]]
    entities += dictionary.detect(text, watchlist or [])

    for e in entities:
        e.canonical = e.canonical or canonical(e.value, e.type)

    allowed = {canonical(a) for a in (allowlist or [])}
    entities = [e for e in entities if e.canonical not in allowed]

    entities = _resolve_overlaps(entities)
    entities = _resolve_overlaps(entities + _propagate(text, entities))
    entities = [e for e in entities if canonical(e.value) not in allowed]
    _link_variants(entities)
    return entities


def summary(entities: list[Entity]) -> dict:
    counts: dict[str, int] = {}
    for e in entities:
        counts[e.type] = counts.get(e.type, 0) + 1
    return {
        "total": len(entities),
        "by_type": counts,
        "secrets": sum(1 for e in entities if e.category == Category.SECRET),
    }
