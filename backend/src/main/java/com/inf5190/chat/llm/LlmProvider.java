package com.inf5190.chat.llm;

import java.util.List;

/**
 * Fournisseur d'IA. Ne reçoit QUE du texte déjà anonymisé et vérifié par LeakGuard.
 */
public interface LlmProvider {

    record Turn(String role, String text) {
        public static Turn user(String text) {
            return new Turn("user", text);
        }

        public static Turn assistant(String text) {
            return new Turn("assistant", text);
        }
    }

    record LlmResponse(String text, String stopReason, String model, long inputTokens, long outputTokens) {
    }

    LlmResponse complete(String systemPrompt, List<Turn> conversation);

    /** Nom affiché dans l'interface, ex. « gemma4:e4b ». */
    String modelName();

    /** true si le modèle tourne sur cette machine (rien ne sort). */
    boolean isLocal();

    /** Le fournisseur n'est pas configuré (clé absente) ou a refusé la requête. */
    class LlmUnavailableException extends RuntimeException {
        public LlmUnavailableException(String message) {
            super(message);
        }

        public LlmUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
