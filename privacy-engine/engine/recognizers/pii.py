"""Identifiants personnels détectés par regex + validation (Luhn, mod 97)."""

import re

from engine.models import Category, Entity


def luhn_ok(digits: str) -> bool:
    d = [int(c) for c in digits if c.isdigit()]
    if len(d) < 2:
        return False
    total = 0
    for i, n in enumerate(reversed(d)):
        if i % 2 == 1:
            n *= 2
            if n > 9:
                n -= 9
        total += n
    return total % 10 == 0


def iban_ok(raw: str) -> bool:
    s = re.sub(r"\s", "", raw).upper()
    if not 15 <= len(s) <= 34:
        return False
    rearranged = s[4:] + s[:4]
    number = "".join(str(int(c, 36)) for c in rearranged)
    return int(number) % 97 == 1


def nir_ok(raw: str) -> bool:
    s = re.sub(r"\s", "", raw).upper().replace("2A", "19").replace("2B", "18")
    if len(s) != 15 or not s.isdigit():
        return False
    return 97 - int(s[:13]) % 97 == int(s[13:])


_STREET = (r"(?:rue|avenue|av\.|boulevard|boul\.|bd|chemin|ch\.|place|allée|impasse|route|rang|"
           r"street|st\.|road|rd\.|drive|dr\.|côte|montée|croissant|quai|cours)")

# (type, motif, score, validateur)
_MONTHS = r"(?:janvier|février|fevrier|mars|avril|mai|juin|juillet|août|aout|septembre|octobre|novembre|décembre|decembre)"
_DATE = r"(?:\d{1,2}(?:er)?\s+" + _MONTHS + r"\s+\d{4}|\d{1,2}[/.\-]\d{1,2}[/.\-]\d{2,4}|\d{4}-\d{2}-\d{2})"

_RULES = [
    ("DATE_NAISSANCE",
     r"(?i)(?<=\bn[ée]\s)(?:le\s+)?" + _DATE + r"|(?<=\bn[ée]e\s)(?:le\s+)?" + _DATE +
     r"|(?<=date de naissance\s:\s)" + _DATE + r"|(?<=date de naissance\s)" + _DATE, 0.9, None),
    ("EMAIL", r"\b[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}\b", 0.99, None),
    ("TELEPHONE", r"(?<![\d\w])(?:\+?1[\s.\-]?)?\(?[2-9]\d{2}\)?[\s.\-]?\d{3}[\s.\-]?\d{4}(?!\d)", 0.85, None),
    ("TELEPHONE", r"(?<![\d\w])(?:\+33\s?|0)[1-9](?:[\s.\-]?\d{2}){4}(?!\d)", 0.85, None),
    ("NAS", r"(?<!\d)\d{3}[\s\-]?\d{3}[\s\-]?\d{3}(?!\d)", 0.9, luhn_ok),
    ("NIR", r"(?<!\d)[12]\s?\d{2}\s?(?:0[1-9]|1[0-2])\s?(?:\d{2}|2A|2B)\s?\d{3}\s?\d{3}\s?\d{2}(?!\d)", 0.95, nir_ok),
    ("IBAN", r"\b[A-Z]{2}\d{2}(?:\s?[A-Z0-9]{4}){2,7}(?:\s?[A-Z0-9]{1,4})?\b", 0.95, iban_ok),
    ("CARTE_BANCAIRE", r"(?<!\d)(?:\d[ \-]?){12,18}\d(?!\d)", 0.9, luhn_ok),
    ("CODE_POSTAL", r"(?i)\b[ABCEGHJ-NPRSTVXY]\d[ABCEGHJ-NPRSTV-Z]\s?\d[ABCEGHJ-NPRSTV-Z]\d\b", 0.8, None),
    # Numéro + type de voie + nom (quelle que soit la casse), jusqu'à la virgule ou la fin de ligne.
    ("ADRESSE",
     r"(?i)\b\d{1,5}(?:\s?(?:bis|ter|[a-z]))?,?\s+" + _STREET + r"\s+[^,\n\d]{2,50}?(?=\s*(?:,|\n|$))",
     0.8, None),
]

# « Adresse de livraison : rue de la Montagne O, Montréal… » → la partie avant la virgule.
_LABELLED_ADDRESS = re.compile(
    r"(?im)\b(?:adresse|address)\b[^:\n]{0,30}:[ \t]*([^,\n]{3,60})")
_RULES = [(t, re.compile(p), s, v) for t, p, s, v in _RULES]

_IP = re.compile(r"\b(?:(?:25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(?:25[0-5]|2[0-4]\d|1?\d?\d)\b")


def detect(text: str) -> list[Entity]:
    found: list[Entity] = []
    for etype, pattern, score, validator in _RULES:
        for m in pattern.finditer(text):
            if validator and not validator(m.group(0)):
                continue
            start, value = m.start(), m.group(0)
            if etype == "DATE_NAISSANCE" and value.lower().startswith("le "):  # « née le 12 mars » : garder « le »
                start, value = start + 3, value[3:]
            found.append(Entity(etype, Category.PII, start, start + len(value), value, score, "regex"))
    for m in _LABELLED_ADDRESS.finditer(text):
        if m.group(1).lstrip().startswith("["):  # déjà un jeton ([ADRESSE_1]) : rien à masquer
            continue
        found.append(Entity("ADRESSE", Category.PII, m.start(1), m.end(1), m.group(1).rstrip(), 0.75, "regex"))
    for m in _IP.finditer(text):
        found.append(Entity("IP", Category.INFRA, m.start(), m.end(), m.group(0), 0.8, "regex"))
    return found
