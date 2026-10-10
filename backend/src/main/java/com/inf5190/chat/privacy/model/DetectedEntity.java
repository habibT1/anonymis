package com.inf5190.chat.privacy.model;

/**
 * Entité sensible détectée par le privacy-engine.
 *
 * @param type      ex. PERSONNE, EMAIL, CLE_API
 * @param category  pii, secret, infra, business
 * @param start     position de début dans le texte analysé
 * @param end       position de fin (exclue)
 * @param value     valeur réelle
 * @param canonical forme normalisée : même canonical = même jeton
 */
public record DetectedEntity(String type, String category, int start, int end,
        String value, double score, String source, String canonical) {

    public boolean isSecret() {
        return "secret".equals(category);
    }
}
