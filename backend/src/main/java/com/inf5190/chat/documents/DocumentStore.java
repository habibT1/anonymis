package com.inf5190.chat.documents;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Documents importés, gardés en mémoire pour le MVP (rien n'est écrit sur disque ni dans Firestore).
 * TODO Phase 2+ : stockage chiffré AES-256-GCM dans Firebase Storage.
 */
@Component
public class DocumentStore {

    public record StoredDocument(String id, String owner, String filename, String text,
            int pageCount, boolean needsOcr, Instant uploadedAt, byte[] pdf) {
    }

    /** Vue publique d'un document : jamais le texte. */
    public record DocumentSummary(String id, String filename, int pageCount, boolean needsOcr,
            int characters, Instant uploadedAt) {
        public static DocumentSummary of(StoredDocument d) {
            return new DocumentSummary(d.id(), d.filename(), d.pageCount(), d.needsOcr(),
                    d.text().length(), d.uploadedAt());
        }
    }

    private final Map<String, StoredDocument> documents = new ConcurrentHashMap<>();

    public StoredDocument save(String owner, String filename, String text, int pageCount, boolean needsOcr) {
        return save(owner, filename, text, pageCount, needsOcr, new byte[0]);
    }

    public StoredDocument save(String owner, String filename, String text, int pageCount, boolean needsOcr,
            byte[] pdf) {
        StoredDocument doc = new StoredDocument(UUID.randomUUID().toString(), owner, filename, text,
                pageCount, needsOcr, Instant.now(), pdf);
        documents.put(doc.id(), doc);
        return doc;
    }

    public Optional<StoredDocument> find(String owner, String id) {
        return Optional.ofNullable(documents.get(id)).filter(d -> d.owner().equals(owner));
    }

    public List<DocumentSummary> list(String owner) {
        return documents.values().stream()
                .filter(d -> d.owner().equals(owner))
                .sorted(Comparator.comparing(StoredDocument::uploadedAt))
                .map(DocumentSummary::of)
                .toList();
    }

    public boolean delete(String owner, String id) {
        return find(owner, id).map(d -> documents.remove(id) != null).orElse(false);
    }
}
