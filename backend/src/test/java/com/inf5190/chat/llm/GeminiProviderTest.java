package com.inf5190.chat.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.inf5190.chat.llm.LlmProvider.Turn;

class GeminiProviderTest {
    private static final String URL = "https://api.test/v1beta";

    private GeminiProvider provider(RestClient.Builder builder, String key) {
        return new GeminiProvider(builder, URL, "gemma-4-26b-a4b-it", "minimal", key);
    }

    @Test
    void sendsSystemInstructionRolesAndThinkingAndSkipsThoughtParts() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL + "/models/gemma-4-26b-a4b-it:generateContent"))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("SYS"))
                .andExpect(jsonPath("$.contents[0].role").value("user"))
                .andExpect(jsonPath("$.contents[1].role").value("model"))
                .andExpect(jsonPath("$.generationConfig.thinkingConfig.thinkingLevel").value("minimal"))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"role":"model","parts":[
                          {"text":"je réfléchis…","thought":true},
                          {"text":"[PERSONNE_1] doit donner le préavis."}]},
                          "finishReason":"STOP"}],
                         "usageMetadata":{"promptTokenCount":12,"candidatesTokenCount":7}}
                        """, MediaType.APPLICATION_JSON));

        var r = provider(builder, "test-key").complete("SYS",
                List.of(Turn.user("Q1"), Turn.assistant("R1"), Turn.user("Q2")));

        assertEquals("[PERSONNE_1] doit donner le préavis.", r.text());
        assertEquals(12, r.inputTokens());
        server.verify();
    }

    @Test
    void missingKeyFailsBeforeAnyCall() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        assertThrows(LlmProvider.LlmUnavailableException.class,
                () -> provider(builder, "").complete("SYS", List.of(Turn.user("Q"))));
        server.verify();
    }

    @Test
    void apiErrorBecomesLlmUnavailable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL + "/models/gemma-4-26b-a4b-it:generateContent"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        var e = assertThrows(LlmProvider.LlmUnavailableException.class,
                () -> provider(builder, "k").complete("SYS", List.of(Turn.user("Q"))));
        assertEquals("Erreur de l'API Gemini (429)", e.getMessage());
    }
}
