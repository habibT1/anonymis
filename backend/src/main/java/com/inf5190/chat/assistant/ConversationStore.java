package com.inf5190.chat.assistant;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.inf5190.chat.llm.LlmProvider.Turn;
import com.inf5190.chat.privacy.TokenVault;
import com.inf5190.chat.privacy.model.DetectionOptions;

/**
 * Conversations en mémoire. L'historique est gardé ANONYMISÉ : c'est ce qu'on renvoie à l'IA à chaque tour.
 */
@Component
public class ConversationStore {
    private static final Duration TTL = Duration.ofHours(2);

    public static final class Conversation {
        final String id;
        final String owner;
        final DetectionOptions options;
        final List<Turn> turns = new ArrayList<>();
        final Set<String> includedDocuments = new HashSet<>();
        Instant lastAccess = Instant.now();

        Conversation(String id, String owner, DetectionOptions options) {
            this.id = id;
            this.owner = owner;
            this.options = options;
        }

        public String id() {
            return id;
        }

        public DetectionOptions options() {
            return options;
        }
    }

    private final Map<String, Conversation> conversations = new ConcurrentHashMap<>();
    private final TokenVault tokenVault;

    public ConversationStore(TokenVault tokenVault) {
        this.tokenVault = tokenVault;
    }

    public Conversation create(String owner, DetectionOptions options) {
        evictExpired();
        Conversation c = new Conversation(UUID.randomUUID().toString(), owner,
                options == null ? DetectionOptions.standard() : options);
        conversations.put(c.id, c);
        return c;
    }

    public Optional<Conversation> find(String owner, String id) {
        evictExpired();
        Optional<Conversation> c = Optional.ofNullable(conversations.get(id)).filter(x -> x.owner.equals(owner));
        c.ifPresent(x -> x.lastAccess = Instant.now());
        return c;
    }

    private void evictExpired() {
        Instant limit = Instant.now().minus(TTL);
        conversations.values().removeIf(c -> {
            boolean expired = c.lastAccess.isBefore(limit);
            if (expired) {
                tokenVault.forget(c.id);
            }
            return expired;
        });
    }
}
