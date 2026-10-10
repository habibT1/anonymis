package com.inf5190.chat.config;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.inf5190.chat.llm.LlmProvider.LlmUnavailableException;
import com.inf5190.chat.privacy.LeakGuard.PiiLeakException;
import com.inf5190.chat.privacy.PrivacyEngineClient.PrivacyEngineUnavailableException;

/**
 * Erreurs du pipeline d'anonymisation, renvoyées en JSON. Ne contiennent jamais de valeur sensible.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(PiiLeakException.class)
    public ResponseEntity<Map<String, Object>> leak(PiiLeakException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "error", "leak_blocked",
                "message", e.getMessage(),
                "types", e.distinctTypes()));
    }

    @ExceptionHandler(PrivacyEngineUnavailableException.class)
    public ResponseEntity<Map<String, Object>> engineDown(PrivacyEngineUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", "privacy_engine_unavailable",
                "message", "Le service d'anonymisation ne répond pas : rien n'a été envoyé à l'IA."));
    }

    @ExceptionHandler(LlmUnavailableException.class)
    public ResponseEntity<Map<String, Object>> llmDown(LlmUnavailableException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", "llm_unavailable",
                "message", e.getMessage()));
    }
}
