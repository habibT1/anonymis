package com.inf5190.chat.privacy;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.inf5190.chat.privacy.model.DetectedEntity;

/**
 * Table de correspondance valeur réelle ↔ jeton, par conversation.
 * Gardée uniquement en mémoire, jamais envoyée à l'IA ni écrite en clair.
 */
@Component
public class TokenVault {
    static final Duration TTL = Duration.ofHours(2);

    // [PERSONNE_1], tolère les petites déformations de l'IA : [Personne 1], [ PERSONNE_1 ]
    private static final Pattern TOKEN = Pattern.compile("\\[\\s*([A-Za-zÀ-ÿ_ ]+?)[ _](\\d+)\\s*]");
    private static final Pattern TITLE = Pattern.compile("^(?i)(m\\.|mme|mlle|mr|mrs|ms|dr|me|pr)\\s+");

    private final Map<String, Vault> vaults = new ConcurrentHashMap<>();

    /** Résultat de la restauration : texte final et jetons inconnus (inventés par l'IA). */
    public record Restored(String text, List<String> unknownTokens) {
    }

    private static final class Vault {
        final Map<String, String> tokenByKey = new HashMap<>();   // "PERSONNE|sophie lavoie" → [PERSONNE_1]
        final Map<String, String> valueByToken = new HashMap<>(); // [PERSONNE_1] → Sophie Lavoie
        final Set<String> secretTokens = new LinkedHashSet<>();
        final Map<String, String> knownValues = new HashMap<>(); // variante vue → type, pour LeakGuard
        final Map<String, String> tokenByKnownValue = new HashMap<>(); // variante vue → son jeton
        final Map<String, Integer> counters = new HashMap<>();
        Instant lastAccess = Instant.now();
    }

    private Vault vault(String conversationId) {
        evictExpired();
        Vault v = vaults.computeIfAbsent(conversationId, id -> new Vault());
        v.lastAccess = Instant.now();
        return v;
    }

    private void evictExpired() {
        Instant limit = Instant.now().minus(TTL);
        vaults.entrySet().removeIf(e -> e.getValue().lastAccess.isBefore(limit));
    }

    /**
     * Remplace chaque entité par son jeton. La même entité (même type + canonical) reçoit
     * toujours le même jeton dans la conversation, même d'un document à l'autre.
     */
    public synchronized String tokenize(String conversationId, String text, List<DetectedEntity> entities) {
        Vault v = vault(conversationId);
        List<DetectedEntity> sorted = new ArrayList<>(entities);
        sorted.sort(Comparator.comparingInt(DetectedEntity::start));
        // Numérotation dans l'ordre de lecture, puis remplacement depuis la fin pour garder les positions valides.
        List<String> tokens = sorted.stream().map(e -> tokenFor(v, e)).toList();

        StringBuilder out = new StringBuilder(text);
        for (int i = sorted.size() - 1; i >= 0; i--) {
            out.replace(sorted.get(i).start(), sorted.get(i).end(), tokens.get(i));
        }
        return out.toString();
    }

    /** Jeton de chaque entité (dans l'ordre donné), sans modifier le texte. Utilisé pour le PDF caviardé. */
    public synchronized List<String> tokensFor(String conversationId, List<DetectedEntity> entities) {
        Vault v = vault(conversationId);
        return entities.stream().map(e -> tokenFor(v, e)).toList();
    }

    private String tokenFor(Vault v, DetectedEntity e) {
        String key = e.type() + "|" + e.canonical();
        String token = v.tokenByKey.computeIfAbsent(key, k -> {
            int n = v.counters.merge(e.type(), 1, Integer::sum);
            return "[" + e.type() + "_" + n + "]";
        });
        String display = TITLE.matcher(e.value().strip()).replaceFirst("");
        // On garde la forme la plus complète pour la restitution (« Sophie Lavoie » plutôt que « Lavoie »).
        v.valueByToken.merge(token, display, (old, neu) -> neu.length() > old.length() ? neu : old);
        v.knownValues.put(e.value().strip(), e.type());
        v.knownValues.put(display, e.type()); // « Mme Sophie Lavoie » → aussi « Sophie Lavoie »
        v.tokenByKnownValue.put(e.value().strip(), token);
        v.tokenByKnownValue.put(display, token);
        if (e.type().equals("PERSONNE")) {
            // Le prénom ou le nom seul (« habib », « traore ») désigne la même personne.
            for (String part : display.split("\\s+")) {
                if (part.length() >= 3) {
                    v.tokenByKnownValue.putIfAbsent(part, token);
                    v.knownValues.putIfAbsent(part, e.type()); // LeakGuard aussi : filet de sécurité
                }
            }
        }
        if (e.isSecret()) {
            v.secretTokens.add(token);
        }
        return token;
    }

    /**
     * Remet les vraies valeurs dans la réponse de l'IA.
     *
     * @param revealSecrets false pour le chat : les secrets restent masqués (•••• (CLE_API_1))
     */
    public synchronized Restored detokenize(String conversationId, String text, boolean revealSecrets) {
        Vault v = vault(conversationId);
        List<String> unknown = new ArrayList<>();
        Matcher m = TOKEN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String token = "[" + m.group(1).strip().toUpperCase().replace(' ', '_') + "_" + m.group(2) + "]";
            String value = v.valueByToken.get(token);
            String replacement;
            if (value == null) {
                unknown.add(m.group());
                replacement = m.group();
            } else if (v.secretTokens.contains(token) && !revealSecrets) {
                replacement = "•••• (" + token.substring(1, token.length() - 1) + ")";
            } else {
                replacement = value;
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return new Restored(out.toString(), unknown);
    }

    /** Résultat du balayage : texte et nombre de remplacements par type. */
    public record Swept(String text, Map<String, Integer> countsByType) {
    }

    /**
     * Masque les valeurs DÉJÀ connues de la conversation que le détecteur a ratées
     * (ex. « habib » tapé en minuscules alors que « Habib Traore » est dans le document).
     * Mots entiers, insensible à la casse et aux accents ; jamais à l'intérieur d'un jeton.
     */
    public synchronized Swept sweepKnown(String conversationId, String text) {
        Vault v = vault(conversationId);
        Map<String, Integer> counts = new HashMap<>();
        List<Map.Entry<String, String>> known = new ArrayList<>(v.tokenByKnownValue.entrySet());
        known.sort((a, b) -> b.getKey().length() - a.getKey().length()); // la plus longue d'abord
        String out = text;
        for (Map.Entry<String, String> k : known) {
            String value = k.getKey();
            if (value.length() < 3) {
                continue;
            }
            String folded = foldSameLength(out);
            Matcher m = wordPattern(foldSameLength(value)).matcher(folded);
            StringBuilder sb = new StringBuilder();
            int last = 0;
            while (m.find()) {
                if (insideToken(out, m.start())) {
                    continue;
                }
                sb.append(out, last, m.start()).append(k.getValue());
                last = m.end();
                counts.merge(typeOf(k.getValue()), 1, Integer::sum);
            }
            sb.append(out.substring(last));
            out = sb.toString();
        }
        return new Swept(out, counts);
    }

    private static Pattern wordPattern(String foldedValue) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(foldedValue).replace(" ", "\\E\\s+\\Q")
                + "(?![\\p{L}\\p{N}])");
    }

    private static boolean insideToken(String text, int index) {
        int open = text.lastIndexOf('[', index);
        int close = text.lastIndexOf(']', index);
        return open > close && text.indexOf(']', index) > 0;
    }

    private static String typeOf(String token) {
        return token.substring(1, token.lastIndexOf('_'));
    }

    /** Minuscule sans accent, 1 caractère → 1 caractère (les positions restent valides). */
    static String foldSameLength(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            String base = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
            sb.append(base.length() == 1 ? Character.toLowerCase(base.charAt(0)) : Character.toLowerCase(c));
        }
        return sb.toString();
    }

    /** Valeurs réelles connues de la conversation → leur type (utilisé par LeakGuard). */
    public synchronized Map<String, String> knownValues(String conversationId) {
        return Map.copyOf(vault(conversationId).knownValues);
    }

    public void forget(String conversationId) {
        vaults.remove(conversationId);
    }
}
