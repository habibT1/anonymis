"""Types partagés du moteur de détection."""

from dataclasses import dataclass, field
from enum import Enum


class Category(str, Enum):
    PII = "pii"            # données personnelles
    SECRET = "secret"      # clés d'API, mots de passe, jetons…
    INFRA = "infra"        # IP, hôtes internes
    BUSINESS = "business"  # noms métier (liste de surveillance)


class Level(str, Enum):
    STANDARD = "standard"  # personnes + identifiants + secrets + liste de surveillance
    STRICT = "strict"      # + organisations, lieux


# Priorité en cas de chevauchement : la plus haute gagne.
PRIORITY = {
    Category.SECRET: 40,
    Category.INFRA: 30,
    Category.PII: 20,
    Category.BUSINESS: 25,
}


@dataclass
class Entity:
    type: str               # ex. PERSONNE, EMAIL, CLE_API
    category: Category
    start: int
    end: int
    value: str
    score: float
    source: str             # regex, ner, dictionnaire, propagation
    canonical: str = ""     # forme normalisée : deux entités de même canonical → même jeton
    priority: int = field(default=0, repr=False)

    def __post_init__(self):
        if not self.priority:
            self.priority = PRIORITY[self.category]

    def overlaps(self, other: "Entity") -> bool:
        return self.start < other.end and other.start < self.end

    def to_dict(self) -> dict:
        return {
            "type": self.type,
            "category": self.category.value,
            "start": self.start,
            "end": self.end,
            "value": self.value,
            "score": round(self.score, 2),
            "source": self.source,
            "canonical": self.canonical,
        }
