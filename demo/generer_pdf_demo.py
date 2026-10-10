"""Génère des PDF de démonstration 100 % FICTIFS (aucune personne réelle).

    ../privacy-engine/.venv/bin/python generer_pdf_demo.py
"""

import pymupdf

CONTRAT = """CONTRAT DE TRAVAIL À DURÉE INDÉTERMINÉE

Entre : Acme Solutions inc., 500 boulevard René-Lévesque Ouest, Montréal (Québec) H3B 1A1, ci-après « l'Employeur »,
et : Mme Sophie Lavoie, née le 12 mars 1990, NAS 046 454 286, demeurant au 88 rue des Érables, Laval H7N 2K4,
ci-après « la Salariée ».

Article 1 – Poste. La Salariée occupe le poste d'analyste principale au sein de l'équipe du Projet Faucon.

Article 2 – Rémunération. Le salaire annuel brut est de 72 000 $, versé toutes les deux semaines
sur le compte IBAN FR76 3000 6000 0112 3456 7890 189.

Article 3 – Coordonnées. Courriel : sophie.lavoie@courriel.ca – Téléphone : 450 555 0123.
Personne à prévenir en cas d'urgence : M. Marc Tremblay, 514 555 0199.

Article 4 – Résiliation. Mme Lavoie doit donner un préavis écrit de deux semaines à l'Employeur.
L'Employeur doit donner un préavis de quatre semaines, sauf faute grave.

Article 5 – Confidentialité. La Salariée ne divulguera aucune information sur le Projet Faucon
ni sur le client Banque Laurentienne, pendant le contrat et deux ans après sa fin.

Article 6 – Accès techniques. Les accès de développement sont remis séparément.
Par erreur, la configuration suivante a été jointe au contrat :
DB_PASSWORD=Hunter2024!
OPENAI_API_KEY=sk-proj-FAKEfakeFAKEfake1234567890abcdef

Fait à Montréal, le 1er septembre 2026.
Signatures : Sophie Lavoie — Jean Dupont, directeur des ressources humaines
"""


def write(path: str, text: str) -> None:
    doc = pymupdf.open()
    page = doc.new_page()
    page.insert_textbox(pymupdf.Rect(50, 50, 545, 800), text, fontsize=9.5, fontname="helv")
    doc.set_metadata({"title": "Contrat de travail (fictif)", "author": "Jean Dupont"})
    doc.save(path)


if __name__ == "__main__":
    write("contrat-fictif.pdf", CONTRAT)
    print("contrat-fictif.pdf généré")
