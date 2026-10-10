package com.inf5190.chat.privacy;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inf5190.chat.privacy.model.DetectedEntity;
import com.inf5190.chat.privacy.model.DetectionOptions;
import com.inf5190.chat.privacy.model.ExtractedDocument;

/**
 * Client du service local privacy-engine (Python).
 */
@Component
public class PrivacyEngineClient {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient restClient;

    public PrivacyEngineClient(RestClient.Builder builder,
            @Value("${privacy.engine.url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    private record DetectResponse(List<DetectedEntity> entities) {
    }

    public List<DetectedEntity> detect(String text, DetectionOptions options) {
        try {
            DetectResponse response = restClient.post()
                    .uri("/detect")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "text", text,
                            "level", options.level(),
                            "watchlist", options.watchlist(),
                            "allowlist", options.allowlist()))
                    .retrieve()
                    .body(DetectResponse.class);
            return response == null ? List.of() : response.entities();
        } catch (RestClientException e) {
            throw new PrivacyEngineUnavailableException(e);
        }
    }

    public ExtractedDocument extract(byte[] pdf) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(pdf) {
            @Override
            public String getFilename() {
                return "document.pdf"; // le vrai nom peut contenir des données personnelles
            }
        });
        try {
            return restClient.post()
                    .uri("/extract")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(ExtractedDocument.class);
        } catch (RestClientException e) {
            throw new PrivacyEngineUnavailableException(e);
        }
    }

    /** Zone à caviarder, en positions du texte renvoyé par /extract. */
    public record RedactionSpan(int start, int end, String label) {
    }

    public byte[] redact(byte[] pdf, List<RedactionSpan> spans) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(pdf) {
            @Override
            public String getFilename() {
                return "document.pdf";
            }
        });
        try {
            form.add("spans", JSON.writeValueAsString(spans));
            return restClient.post()
                    .uri("/redact")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(byte[].class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        } catch (RestClientException e) {
            throw new PrivacyEngineUnavailableException(e);
        }
    }

    public byte[] render(String title, String text) {
        try {
            return restClient.post()
                    .uri("/render")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("title", title, "text", text))
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientException e) {
            throw new PrivacyEngineUnavailableException(e);
        }
    }

    /**
     * Le service de détection ne répond pas : on refuse plutôt que d'envoyer du texte non vérifié.
     */
    public static class PrivacyEngineUnavailableException extends RuntimeException {
        public PrivacyEngineUnavailableException(Throwable cause) {
            super("privacy-engine indisponible", cause);
        }
    }
}
