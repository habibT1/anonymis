package com.inf5190.chat.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Modèle local via Ollama (par défaut Gemma 4 de Google). Mode « zéro sortie » : rien ne quitte la machine.
 */
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "ollama")
public class OllamaProvider implements LlmProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(OllamaProvider.class);

    private final RestClient restClient;
    private final String model;
    private final int numCtx;
    private final boolean think;

    public OllamaProvider(RestClient.Builder builder,
            @Value("${llm.ollama.url}") String url,
            @Value("${llm.ollama.model}") String model,
            @Value("${llm.ollama.num-ctx}") int numCtx,
            @Value("${llm.ollama.think}") boolean think) {
        this.restClient = builder.baseUrl(url).build();
        this.model = model;
        this.numCtx = numCtx;
        this.think = think;
    }

    private record ChatMessage(String role, String content) {
    }

    private record ChatResponse(ChatMessage message, String model,
            @JsonProperty("done_reason") String doneReason,
            @JsonProperty("prompt_eval_count") long promptEvalCount,
            @JsonProperty("eval_count") long evalCount) {
    }

    @Override
    public LlmResponse complete(String systemPrompt, List<Turn> conversation) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", systemPrompt));
        conversation.forEach(t -> messages.add(new ChatMessage(t.role(), t.text())));

        try {
            ChatResponse response = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", model,
                            "messages", messages,
                            "stream", false,
                            // Gemma 4 « réfléchit » par défaut : ~7x plus lent pour un gain faible sur du Q&A.
                            "think", think,
                            // Le contexte par défaut d'Ollama est petit : un document serait tronqué sans prévenir.
                            "options", Map.of("num_ctx", numCtx, "temperature", 0.2)))
                    .retrieve()
                    .body(ChatResponse.class);
            if (response == null || response.message() == null) {
                throw new LlmUnavailableException("Réponse vide d'Ollama");
            }
            return new LlmResponse(response.message().content(), response.doneReason(), response.model(),
                    response.promptEvalCount(), response.evalCount());
        } catch (RestClientException e) {
            LOGGER.warn("Erreur Ollama ({}) : {}", model, e.getClass().getSimpleName());
            throw new LlmUnavailableException(
                    "Ollama ne répond pas ou le modèle « " + model + " » n'est pas installé (ollama pull " + model + ")", e);
        }
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public boolean isLocal() {
        return true;
    }
}
