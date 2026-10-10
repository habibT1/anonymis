package com.inf5190.chat.privacy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.inf5190.chat.privacy.model.DetectedEntity;
import com.inf5190.chat.privacy.model.DetectionOptions;

class LeakGuardTest {
    private final TokenVault vault = new TokenVault();
    private LeakGuard guard;

    @BeforeEach
    void setUp() {
        PrivacyEngineClient engine = mock(PrivacyEngineClient.class);
        when(engine.detect(anyString(), any())).thenReturn(List.of());
        guard = new LeakGuard(engine, vault);
        String doc = "Signé par Mme Sophie Lavoie et Paul Roy.";
        vault.tokenize("c", doc, List.of(
                TokenVaultTest.entity(doc, "Mme Sophie Lavoie", "PERSONNE", "pii", "sophie lavoie"),
                TokenVaultTest.entity(doc, "Paul Roy", "PERSONNE", "pii", "paul roy")));
    }

    @Test
    void blocksKnownValueAndReportsItsType() {
        var e = assertThrows(LeakGuard.PiiLeakException.class,
                () -> guard.check("c", "et que fait sophie   lavoie ?", DetectionOptions.standard()));
        assertEquals(Set.of("PERSONNE"), e.distinctTypes());
    }

    @Test
    void blocksFirstNameAloneEvenWithoutSweep() {
        assertThrows(LeakGuard.PiiLeakException.class,
                () -> guard.check("c", "sophie a combien ?", DetectionOptions.standard()));
    }

    @Test
    void ignoresTextInsideTokens() {
        String t = "NAS";
        vault.tokenize("c", t, List.of(TokenVaultTest.entity(t, "NAS", "PERSONNE", "pii", "nas")));
        assertDoesNotThrow(() -> guard.check("c", "Son numéro est [NAS_1].", DetectionOptions.standard()));
    }

    @Test
    void doesNotBlockKnownValueInsideAnotherWord() {
        vault.tokenize("c", "Roy", List.of(TokenVaultTest.entity("Roy", "Roy", "PERSONNE", "pii", "paul roy")));
        assertDoesNotThrow(() -> guard.check("c", "Parle-moi du royaume et de Lavoisier.", DetectionOptions.standard()));
    }
}
