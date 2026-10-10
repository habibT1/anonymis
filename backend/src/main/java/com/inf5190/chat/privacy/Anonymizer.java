package com.inf5190.chat.privacy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.inf5190.chat.privacy.model.DetectedEntity;
import com.inf5190.chat.privacy.model.DetectionOptions;

/**
 * Détection + tokenisation d'un texte, avec un résumé de ce qui a été masqué.
 */
@Component
public class Anonymizer {
    private final PrivacyEngineClient privacyEngine;
    private final TokenVault tokenVault;

    public Anonymizer(PrivacyEngineClient privacyEngine, TokenVault tokenVault) {
        this.privacyEngine = privacyEngine;
        this.tokenVault = tokenVault;
    }

    /**
     * @param text       texte anonymisé, prêt pour l'IA
     * @param maskedByType nombre d'entités masquées par type (jamais les valeurs)
     */
    public record Result(String text, Map<String, Integer> maskedByType) {
        public int total() {
            return maskedByType.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    public Result anonymize(String conversationId, String text, DetectionOptions options) {
        List<DetectedEntity> entities = privacyEngine.detect(text, options);
        Map<String, Integer> counts = new HashMap<>();
        entities.forEach(e -> counts.merge(e.type(), 1, Integer::sum));
        String tokenized = tokenVault.tokenize(conversationId, text, entities);
        // Deuxième passe : les valeurs déjà connues que le détecteur a ratées (casse, prénom seul…).
        TokenVault.Swept swept = tokenVault.sweepKnown(conversationId, tokenized);
        swept.countsByType().forEach((k, v) -> counts.merge(k, v, Integer::sum));
        return new Result(swept.text(), counts);
    }
}
