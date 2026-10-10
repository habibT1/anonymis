package com.inf5190.chat.assistant.model;

import java.util.List;

import com.inf5190.chat.privacy.model.DetectionOptions;

/**
 * Question posée à l'assistant.
 *
 * @param conversationId null pour démarrer une nouvelle conversation
 * @param documentIds    documents à joindre (seuls les nouveaux sont envoyés)
 * @param options        niveau de masquage, liste de surveillance / d'autorisation
 *                       (fixé au premier message de la conversation)
 * @param dryRun         true : anonymise et vérifie, renvoie ce qui serait envoyé, sans appeler l'IA
 */
public record AskRequest(String conversationId, String question, List<String> documentIds,
        DetectionOptions options, boolean dryRun) {
}
