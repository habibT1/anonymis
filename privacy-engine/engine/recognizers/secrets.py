"""Détection des secrets par leur forme : clés d'API, clés privées, JWT, chaînes de connexion, .env."""

import re

from engine.models import Category, Entity
from engine.text import shannon_entropy

# (type, motif, score). Motifs inspirés des règles gitleaks.
_KNOWN_KEYS = [
    ("CLE_API", r"\bsk-[A-Za-z0-9_\-]{20,}", 0.95),                        # format sk-… (OpenAI et autres)
    ("CLE_API", r"\b(?:AKIA|ASIA)[0-9A-Z]{16}\b", 0.99),                   # AWS
    ("CLE_API", r"\bAIza[0-9A-Za-z_\-]{35}\b", 0.99),                      # Google / Firebase
    ("CLE_API", r"\b(?:gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{22,})\b", 0.99),  # GitHub
    ("CLE_API", r"\bxox[abprs]-[A-Za-z0-9\-]{10,}", 0.99),                 # Slack
    ("CLE_API", r"\b(?:sk|rk)_(?:live|test)_[A-Za-z0-9]{16,}\b", 0.99),    # Stripe
    ("CLE_API", r"\bglpat-[A-Za-z0-9_\-]{20,}\b", 0.99),                   # GitLab
    ("CLE_PRIVEE",
     r"-----BEGIN [A-Z ]*PRIVATE KEY-----[\s\S]*?-----END [A-Z ]*PRIVATE KEY-----", 1.0),
    ("JETON", r"\beyJ[A-Za-z0-9_\-]{5,}\.eyJ[A-Za-z0-9_\-]{5,}\.[A-Za-z0-9_\-]{5,}", 0.95),  # JWT
]
_KNOWN_KEYS = [(t, re.compile(p), s) for t, p, s in _KNOWN_KEYS]

# postgres://user:motdepasse@hote:5432/db → on masque « user:motdepasse@hote »
_CONNECTION = re.compile(r"\b[a-zA-Z][a-zA-Z0-9+.\-]*://([^\s:/@]+:[^\s@/]+@[^\s/:?#]+)")

_SECRET_NAME = r"[A-Za-z0-9_.\-]*(?:KEY|SECRET|TOKEN|PASSWORD|PASSWD|PWD|CREDENTIAL|PRIVATE|AUTH)[A-Za-z0-9_.\-]*"
# NOM=valeur, NOM: valeur, "nom": "valeur" (.env, YAML, JSON, properties)
_ASSIGNMENT = re.compile(
    r"""(?im)(?:^|[\s{,;(])["']?(""" + _SECRET_NAME + r""")["']?\s*[:=]\s*["']?([^\s"',;#}]{4,})"""
)

# Chaîne aléatoire proche d'un mot-clé, pour les formats maison.
_KEYWORD = re.compile(r"(?i)(key|secret|token|password|passwd|api|bearer|auth)")
_RANDOM = re.compile(r"[A-Za-z0-9+/_\-]{24,}={0,2}")  # « = » seulement en fin (base64), pas « NOM=valeur »

_PLACEHOLDERS = {"changeme", "xxxx", "your_key_here", "null", "none", "true", "false", "example"}


def detect(text: str) -> list[Entity]:
    found: list[Entity] = []

    for etype, pattern, score in _KNOWN_KEYS:
        for m in pattern.finditer(text):
            found.append(Entity(etype, Category.SECRET, m.start(), m.end(), m.group(0), score, "regex"))

    for m in _CONNECTION.finditer(text):
        found.append(Entity("CONNEXION", Category.SECRET, m.start(1), m.end(1), m.group(1), 0.95, "regex"))

    for m in _ASSIGNMENT.finditer(text):
        value = m.group(2)
        if value.lower() in _PLACEHOLDERS or value.startswith("${") or value.startswith("["):
            continue
        found.append(Entity("SECRET", Category.SECRET, m.start(2), m.end(2), value, 0.85, "regex"))

    for m in _RANDOM.finditer(text):
        token = m.group(0)
        has_mix = any(c.isdigit() for c in token) and any(c.isalpha() for c in token)
        line_start = text.rfind("\n", 0, m.start()) + 1
        near_keyword = _KEYWORD.search(text[max(line_start, m.start() - 40):m.start()])
        if has_mix and near_keyword and shannon_entropy(token) >= 4.0:
            # Priorité plus basse : une règle précise (CLE_API, NOM=valeur) doit l'emporter.
            found.append(Entity("SECRET", Category.SECRET, m.start(), m.end(), token, 0.6, "entropie",
                                priority=35))

    return found
