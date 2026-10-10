package com.inf5190.chat.privacy.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Texte extrait d'un PDF par le privacy-engine (les positions des mots sont ignorées pour l'instant).
 */
public record ExtractedDocument(
        String text,
        @JsonProperty("page_count") int pageCount,
        @JsonProperty("needs_ocr") boolean needsOcr) {
}
