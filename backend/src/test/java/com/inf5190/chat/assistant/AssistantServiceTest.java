package com.inf5190.chat.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.inf5190.chat.assistant.model.AskRequest;
import com.inf5190.chat.assistant.model.AskResponse;
import com.inf5190.chat.documents.DocumentStore;
import com.inf5190.chat.llm.LlmProvider;
import com.inf5190.chat.privacy.Anonymizer;
import com.inf5190.chat.privacy.LeakGuard;
import com.inf5190.chat.privacy.PrivacyEngineClient;
import com.inf5190.chat.privacy.TokenVault;
import com.inf5190.chat.privacy.model.DetectedEntity;
import com.inf5190.chat.privacy.model.DetectionOptions;

/**
 * Pipeline complet avec un faux privacy-engine (dictionnaire fixe) et une fausse IA qui enregistre ce qu'elle reçoit.
 */
class AssistantServiceTest {
    private static final Map<String, String[]> FAKE_PII = Map.of(
            "Mme Sophie Lavoie", new String[] {"PERSONNE", "pii", "sophie lavoie"},
            "sophie.lavoie@courriel.ca", new String[] {"EMAIL", "pii", "sophie.lavoie@courriel.ca"},
            "046 454 286", new String[] {"NAS", "pii", "046454286"});

    private final List<List<LlmProvider.Turn>> sentToAi = new ArrayList<>();
    private String aiAnswer = "[PERSONNE_1] doit donner un préavis de deux semaines.";
    private PrivacyEngineClient engine;
    private DocumentStore documents;
    private AssistantService service;

    /** Faux détecteur : trouve les valeurs du dictionnaire FAKE_PII. */
    static List<DetectedEntity> fakeDetect(String text) {
        List<DetectedEntity> out = new ArrayList<>();
        FAKE_PII.forEach((value, meta) -> {
            Matcher m = Pattern.compile(Pattern.quote(value)).matcher(text);
            while (m.find()) {
                out.add(new DetectedEntity(meta[0], meta[1], m.start(), m.end(), value, 0.9, "regex", meta[2]));
            }
        });
        return out;
    }

    @BeforeEach
    void setUp() {
        engine = mock(PrivacyEngineClient.class);
        when(engine.detect(anyString(), any())).thenAnswer(inv -> fakeDetect(inv.getArgument(0)));
        TokenVault vault = new TokenVault();
        documents = new DocumentStore();
        LlmProvider fakeAi = new LlmProvider() {
            @Override
            public LlmResponse complete(String system, List<Turn> turns) {
                sentToAi.add(List.copyOf(turns));
                return new LlmResponse(aiAnswer, "end_turn", "fake", 1, 1);
            }

            @Override
            public String modelName() {
                return "fake";
            }

            @Override
            public boolean isLocal() {
                return true;
            }
        };
        service = new AssistantService(new ConversationStore(vault), documents, new Anonymizer(engine, vault),
                new LeakGuard(engine, vault), vault, fakeAi);
    }

    @Test
    void aiNeverSeesRealValuesAndUserSeesThem() {
        var doc = documents.save("alice", "CV_Sophie_Lavoie.pdf",
                "Employée : Mme Sophie Lavoie, NAS 046 454 286, sophie.lavoie@courriel.ca", 1, false);

        AskResponse r = service.ask("alice", new AskRequest(null, "Qui doit donner le préavis ?",
                List.of(doc.id()), DetectionOptions.standard(), false));

        String sent = sentToAi.get(0).get(0).text();
        assertFalse(sent.contains("Sophie"));
        assertFalse(sent.contains("046 454 286"));
        assertFalse(sent.contains("courriel.ca"));
        assertFalse(sent.contains("CV_Sophie"), "le nom du fichier ne doit pas partir");
        assertTrue(sent.contains("[PERSONNE_1]") && sent.contains("[NAS_1]") && sent.contains("[EMAIL_1]"));

        assertEquals("Sophie Lavoie doit donner un préavis de deux semaines.", r.answer());
        assertEquals(3, r.maskedTotal());
        assertEquals(sent, r.aiView().sent());
    }

    @Test
    void followUpReusesTokensAndDoesNotResendDocument() {
        var doc = documents.save("alice", "contrat.pdf", "Salariée : Mme Sophie Lavoie", 1, false);
        AskResponse first = service.ask("alice", new AskRequest(null, "Résume", List.of(doc.id()), null, false));
        service.ask("alice", new AskRequest(first.conversationId(), "Et Mme Sophie Lavoie, quel est son rôle ?",
                List.of(doc.id()), null, false));

        List<LlmProvider.Turn> second = sentToAi.get(1);
        assertEquals(3, second.size()); // question 1, réponse 1, question 2
        assertEquals("Et [PERSONNE_1], quel est son rôle ?", second.get(2).text());
    }

    @Test
    void knownValueMissedByDetectorIsMaskedAutomatically() {
        var doc = documents.save("alice", "contrat.pdf", "Salariée : Mme Sophie Lavoie", 1, false);
        AskResponse first = service.ask("alice", new AskRequest(null, "Résume", List.of(doc.id()), null, false));

        // En minuscules, le faux détecteur ne la voit pas : le balayage des valeurs connues la masque.
        service.ask("alice", new AskRequest(first.conversationId(), "que fait sophie lavoie ? et lavoie ?",
                List.of(), null, false));
        String sent = sentToAi.get(1).get(2).text();
        assertEquals("que fait [PERSONNE_1] ? et [PERSONNE_1] ?", sent);
    }

    @Test
    void dryRunShowsPayloadWithoutCallingAi() {
        var doc = documents.save("alice", "contrat.pdf", "Salariée : Mme Sophie Lavoie", 1, false);
        AskResponse r = service.ask("alice", new AskRequest(null, "Résume", List.of(doc.id()), null, true));
        assertTrue(r.aiView().sent().contains("[PERSONNE_1]"));
        assertEquals(null, r.answer());
        assertTrue(sentToAi.isEmpty());
    }

    @Test
    void otherUsersCannotUseMyDocumentsOrConversations() {
        var doc = documents.save("alice", "contrat.pdf", "Salariée : Mme Sophie Lavoie", 1, false);
        assertThrows(Exception.class, () -> service.ask("bob", new AskRequest(null, "Résume", List.of(doc.id()), null, false)));
        AskResponse r = service.ask("alice", new AskRequest(null, "Résume", List.of(doc.id()), null, false));
        assertThrows(Exception.class, () -> service.ask("bob", new AskRequest(r.conversationId(), "Et ?", List.of(), null, false)));
    }
}
