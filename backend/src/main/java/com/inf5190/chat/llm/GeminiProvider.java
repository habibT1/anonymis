package com.inf5190.chat.llm;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;

/**
 * Gemma 4 (ou un autre modèle) via l'API Gemini de Google AI Studio. La clé est lue dans GEMINI_API_KEY.
 * Seul du texte anonymisé et vérifié par LeakGuard arrive ici.
 */
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "gemini")
public class GeminiProvider implements LlmProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeminiProvider.class);

    private final RestClient restClient;
    private final String model;
    private final String thinkingLevel;
    private final String apiKey;

    public GeminiProvider(RestClient.Builder builder,
            @Value("${llm.gemini.url}") String url,
            @Value("${llm.gemini.model}") String model,
            @Value("${llm.gemini.thinking-level}") String thinkingLevel,
            @Value("${GEMINI_API_KEY:}") String apiKey) {
        this.restClient = builder.baseUrl(url).build();
        this.model = model;
        this.thinkingLevel = thinkingLevel;
        this.apiKey = apiKey;
    }

    private record Part(String text, Boolean thought) {
    }

    private record Content(String role, List<Part> parts) {
    }

    private record Candidate(Content content, String finishReason) {
    }

    private record Usage(long promptTokenCount, long candidatesTokenCount) {
    }

    private record GenerateResponse(List<Candidate> candidates, Usage usageMetadata, String modelVersion) {
    }

    private static Map<String, Object> textContent(String role, String text) {
        return Map.of("role", role, "parts", List.of(Map.of("text", text)));
    }

    @Override
    public LlmResponse complete(String systemPrompt, List<Turn> conversation) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmUnavailableException("GEMINI_API_KEY n'est pas définie (voir README → Configurer les clés)");
        }

        List<Map<String, Object>> contents = conversation.stream()
                .map(t -> textContent("assistant".equals(t.role()) ? "model" : "user", t.text()))
                .toList();

        try {
            GenerateResponse response = restClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                            "contents", contents,
                            "generationConfig", Map.of(
                                    "temperature", 0.2,
                                    // « minimal » : pas de réflexion, réponses bien plus rapides sur du Q&A.
                                    "thinkingConfig", Map.of("thinkingLevel", thinkingLevel))))
                    .retrieve()
                    .body(GenerateResponse.class);

            if (response == null || response.candidates() == null || response.candidates().isEmpty()
                    || response.candidates().get(0).content() == null) {
                throw new LlmUnavailableException("Réponse vide de l'API Gemini (contenu filtré ?)");
            }
            Candidate c = response.candidates().get(0);
            String text = c.content().parts().stream()
                    .filter(p -> p.text() != null && !Boolean.TRUE.equals(p.thought()))
                    .map(Part::text)
                    .collect(Collectors.joining());
            Usage u = response.usageMetadata();
            return new LlmResponse(text, c.finishReason(), model,
                    u == null ? 0 : u.promptTokenCount(), u == null ? 0 : u.candidatesTokenCount());
        } catch (RestClientResponseException e) {
            // Ne jamais journaliser la requête : seulement le statut.
            LOGGER.warn("Erreur API Gemini ({}) : statut {}", model, e.getStatusCode().value());
            throw new LlmUnavailableException("Erreur de l'API Gemini (" + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            LOGGER.warn("API Gemini injoignable : {}", e.getClass().getSimpleName());
            throw new LlmUnavailableException("API Gemini injoignable", e);
        }
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public boolean isLocal() {
        return false;
    }
}
