package com.inf5190.chat.assistant.model;

import java.util.List;
import java.util.Map;

/**
 * Réponse de l'assistant.
 *
 * @param answer        réponse avec les vraies valeurs (secrets toujours masqués)
 * @param aiView        exactement ce que l'IA a reçu et répondu, avec les jetons
 * @param maskedByType  nombre de données masquées dans ce tour, par type
 * @param unknownTokens jetons présents dans la réponse mais absents de la table (inventés par l'IA)
 */
public record AskResponse(String conversationId, String answer, AiView aiView,
        Map<String, Integer> maskedByType, int maskedTotal, List<String> unknownTokens, String model) {

    public record AiView(String sent, String received) {
    }
}
