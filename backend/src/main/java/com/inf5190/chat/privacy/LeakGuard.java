package com.inf5190.chat.privacy;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.inf5190.chat.privacy.model.DetectedEntity;
import com.inf5190.chat.privacy.model.DetectionOptions;

/**
 * Dernière vérification avant l'envoi à l'IA (« fail closed ») :
 * si une valeur réelle traîne encore dans la requête, l'appel est bloqué.
 */
@Component
public class LeakGuard {
    private static final Pattern TOKEN = Pattern.compile("\\[[a-z_]+_\\d+\\]");
    private final PrivacyEngineClient privacyEngine;
    private final TokenVault tokenVault;

    public LeakGuard(PrivacyEngineClient privacyEngine, TokenVault tokenVault) {
        this.privacyEngine = privacyEngine;
        this.tokenVault = tokenVault;
    }

    /**
     * @param payload tout le texte qui va partir vers l'IA (déjà anonymisé)
     * @throws PiiLeakException si une donnée sensible est encore présente
     */
    public void check(String conversationId, String payload, DetectionOptions options) {
        List<String> leakedTypes = new ArrayList<>();

        // 1. Une valeur déjà connue (vue dans un document ou une question) est-elle encore là ?
        //    Mots entiers seulement : « Roy » ne doit pas bloquer « royaume ».
        // Le texte des jetons ne compte pas : « nas » dans « [NAS_1] » n'est pas une fuite.
        String folded = TOKEN.matcher(fold(payload)).replaceAll(" ");
        tokenVault.knownValues(conversationId).forEach((value, type) -> {
            if (value.length() >= 3 && containsWord(folded, fold(value))) {
                leakedTypes.add(type);
            }
        });

        // 2. Nouvelle passe de détection sur la requête complète. On ne garde que les détections
        //    déterministes (regex, dictionnaire) : la NER varie avec le contexte et bloquerait à tort.
        for (DetectedEntity e : privacyEngine.detect(payload, options)) {
            if (!"ner".equals(e.source()) && !"propagation".equals(e.source())) {
                leakedTypes.add(e.type());
            }
        }

        if (!leakedTypes.isEmpty()) {
            throw new PiiLeakException(leakedTypes);
        }
    }

    static boolean containsWord(String text, String value) {
        String pattern = "(?<![\\p{L}\\p{N}])" + Pattern.quote(value).replace(" ", "\\E\\s+\\Q")
                + "(?![\\p{L}\\p{N}])";
        return Pattern.compile(pattern).matcher(text).find();
    }

    static String fold(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    /**
     * Une donnée sensible allait partir vers l'IA. Ne contient que les types, jamais les valeurs.
     */
    public static class PiiLeakException extends RuntimeException {
        private final List<String> types;

        public PiiLeakException(List<String> types) {
            super("Envoi bloqué : " + types.size() + " donnée(s) sensible(s) détectée(s) dans la requête");
            this.types = List.copyOf(types);
        }

        public List<String> types() {
            return types;
        }

        public Set<String> distinctTypes() {
            return Set.copyOf(types);
        }
    }
}
