"""Extraction du texte d'un PDF avec la position de chaque mot (utile pour caviarder ensuite)."""

import pymupdf


def extract(data: bytes) -> dict:
    """Retourne le texte complet et, pour chaque mot, sa page, sa boîte et ses positions dans le texte.
    Le texte est reconstruit à partir des mots : les positions correspondent donc exactement."""
    doc = pymupdf.open(stream=data, filetype="pdf")
    parts: list[str] = []
    pages = []
    offset = 0

    for page in doc:
        words_out = []
        page_start = offset
        prev_key = None
        for x0, y0, x1, y1, word, block, line, _ in page.get_text("words", sort=True):
            key = (block, line)
            if prev_key is not None:
                sep = "\n" if key != prev_key else " "
                parts.append(sep)
                offset += len(sep)
            words_out.append({"text": word, "start": offset, "end": offset + len(word),
                              "bbox": [round(x0, 2), round(y0, 2), round(x1, 2), round(y1, 2)]})
            parts.append(word)
            offset += len(word)
            prev_key = key
        pages.append({"number": page.number + 1, "width": page.rect.width, "height": page.rect.height,
                      "start": page_start, "end": offset, "words": words_out})
        parts.append("\n\n")
        offset += 2

    text = "".join(parts).rstrip("\n")
    scanned = bool(pages) and all(not p["words"] for p in pages)
    return {"text": text, "page_count": len(pages), "pages": pages, "needs_ocr": scanned}


def _words_with_offsets(doc) -> list[tuple[int, int, int, "pymupdf.Rect", tuple]]:
    """Mêmes positions que extract() : (page, start, end, rect, (block, line))."""
    out = []
    offset = 0
    for page in doc:
        prev_key = None
        for x0, y0, x1, y1, word, block, line, _ in page.get_text("words", sort=True):
            key = (block, line)
            if prev_key is not None:
                offset += 1  # « \n » ou « »
            out.append((page.number, offset, offset + len(word), pymupdf.Rect(x0, y0, x1, y1), key))
            offset += len(word)
            prev_key = key
        offset += 2  # « \n\n » entre les pages
    return out


def redact(data: bytes, spans: list[dict]) -> bytes:
    """Caviarde le PDF : chaque zone [start, end) du texte extrait devient un bloc noir portant son jeton.
    Le texte d'origine est réellement supprimé du fichier (pas seulement recouvert)."""
    doc = pymupdf.open(stream=data, filetype="pdf")
    words = _words_with_offsets(doc)
    for span in spans:
        start, end, label = span["start"], span["end"], span.get("label", "")
        hits = [w for w in words if w[1] < end and start < w[2]]
        # Un rectangle par ligne touchée (une entité peut tenir sur deux lignes).
        by_line: dict[tuple, pymupdf.Rect] = {}
        for page_no, _, _, rect, key in hits:
            k = (page_no, key)
            by_line[k] = by_line[k] | rect if k in by_line else pymupdf.Rect(rect)
        for i, ((page_no, _), rect) in enumerate(by_line.items()):
            page = doc[page_no]
            size = max(4.0, min(9.0, rect.height * 0.7))
            page.add_redact_annot(rect, text=label if i == 0 else "", fontname="helv", fontsize=size,
                                  fill=(0.09, 0.09, 0.1), text_color=(1, 1, 1), align=pymupdf.TEXT_ALIGN_CENTER)
    for page in doc:
        page.apply_redactions(images=pymupdf.PDF_REDACT_IMAGE_NONE)
    doc.set_metadata({})  # les métadonnées (auteur, titre) contiennent souvent un nom
    return doc.tobytes(garbage=4, deflate=True)


def render(title: str, text: str) -> bytes:
    """Génère un PDF propre (A4) à partir d'un texte simple."""
    import html

    body = "".join(
        f"<p>{html.escape(line) if line.strip() else '&nbsp;'}</p>" for line in text.splitlines()
    )
    page_html = f"<h1>{html.escape(title)}</h1>{body}"
    css = ("* { font-family: sans-serif; } h1 { font-size: 16pt; margin-bottom: 12pt; } "
           "p { font-size: 10.5pt; line-height: 1.45; margin: 0 0 4pt 0; }")

    import io

    buffer = io.BytesIO()
    writer = pymupdf.DocumentWriter(buffer)
    story = pymupdf.Story(html=page_html, user_css=css)
    rect = pymupdf.paper_rect("a4")
    where = rect + (56, 56, -56, -56)
    more = True
    while more:
        device = writer.begin_page(rect)
        more, _ = story.place(where)
        story.draw(device)
        writer.end_page()
    writer.close()
    return buffer.getvalue()
