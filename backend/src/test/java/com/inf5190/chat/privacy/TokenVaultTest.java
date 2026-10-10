package com.inf5190.chat.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.inf5190.chat.privacy.model.DetectedEntity;

class TokenVaultTest {
    private final TokenVault vault = new TokenVault();

    static DetectedEntity entity(String text, String value, String type, String category, String canonical) {
        int start = text.indexOf(value);
        return new DetectedEntity(type, category, start, start + value.length(), value, 0.9, "test", canonical);
    }

    @Test
    void sameEntityGetsSameTokenAcrossTexts() {
        String a = "Contrat de Mme Sophie Lavoie.";
        String b = "Mme Lavoie doit donner un préavis.";
        String ta = vault.tokenize("c1", a, List.of(entity(a, "Mme Sophie Lavoie", "PERSONNE", "pii", "sophie lavoie")));
        String tb = vault.tokenize("c1", b, List.of(entity(b, "Mme Lavoie", "PERSONNE", "pii", "sophie lavoie")));

        assertEquals("Contrat de [PERSONNE_1].", ta);
        assertEquals("[PERSONNE_1] doit donner un préavis.", tb);
        // Restitution sans le titre et avec la forme la plus complète.
        assertEquals("Sophie Lavoie signe.", vault.detokenize("c1", "[PERSONNE_1] signe.", false).text());
    }

    @Test
    void differentEntitiesGetNumberedTokens() {
        String t = "Jean Dupont écrit à Marie Roy (marie@exemple.ca).";
        String out = vault.tokenize("c2", t, List.of(
                entity(t, "Jean Dupont", "PERSONNE", "pii", "jean dupont"),
                entity(t, "Marie Roy", "PERSONNE", "pii", "marie roy"),
                entity(t, "marie@exemple.ca", "EMAIL", "pii", "marie@exemple.ca")));
        assertEquals("[PERSONNE_1] écrit à [PERSONNE_2] ([EMAIL_1]).", out);
    }

    @Test
    void secretsStayMaskedInChat() {
        String t = "OPENAI_API_KEY=sk-proj-FAKE1234567890abcdefgh";
        vault.tokenize("c3", t, List.of(entity(t, "sk-proj-FAKE1234567890abcdefgh", "CLE_API", "secret", "x")));
        assertEquals("Votre clé •••• (CLE_API_1) est exposée.",
                vault.detokenize("c3", "Votre clé [CLE_API_1] est exposée.", false).text());
        assertEquals("sk-proj-FAKE1234567890abcdefgh", vault.detokenize("c3", "[CLE_API_1]", true).text());
    }

    @Test
    void toleratesDeformedTokensAndReportsUnknownOnes() {
        String t = "Signé par Jean Dupont.";
        vault.tokenize("c4", t, List.of(entity(t, "Jean Dupont", "PERSONNE", "pii", "jean dupont")));
        TokenVault.Restored r = vault.detokenize("c4", "[Personne 1] et [PERSONNE_9] ont signé.", false);
        assertEquals("Jean Dupont et [PERSONNE_9] ont signé.", r.text());
        assertEquals(List.of("[PERSONNE_9]"), r.unknownTokens());
    }

    @Test
    void conversationsAreIsolated() {
        String t = "Jean Dupont";
        vault.tokenize("c5", t, List.of(entity(t, "Jean Dupont", "PERSONNE", "pii", "jean dupont")));
        assertTrue(vault.detokenize("autre", "[PERSONNE_1]", false).unknownTokens().contains("[PERSONNE_1]"));
    }
}
