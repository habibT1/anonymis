package com.inf5190.chat.documents;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.inf5190.chat.auth.AuthController;
import com.inf5190.chat.auth.session.CurrentUser;
import com.inf5190.chat.documents.DocumentStore.DocumentSummary;
import com.inf5190.chat.privacy.PrivacyEngineClient;
import com.inf5190.chat.privacy.model.ExtractedDocument;

/**
 * Import et gestion des PDF. Le texte est extrait en local par le privacy-engine.
 */
@RestController
public class DocumentController {
    public static final String DOCUMENTS_PATH = "/documents";

    private final DocumentStore documentStore;
    private final PrivacyEngineClient privacyEngine;
    private final CurrentUser currentUser;

    public DocumentController(DocumentStore documentStore, PrivacyEngineClient privacyEngine,
            CurrentUser currentUser) {
        this.documentStore = documentStore;
        this.privacyEngine = privacyEngine;
        this.currentUser = currentUser;
    }

    @PostMapping(value = DOCUMENTS_PATH, consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DocumentSummary> upload(
            @CookieValue(value = AuthController.SESSION_ID_COOKIE_NAME, required = false) String sessionId,
            @RequestParam("file") MultipartFile file) throws IOException {
        String owner = currentUser.username(sessionId);
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier vide");
        }
        byte[] pdf = file.getBytes();
        ExtractedDocument extracted = privacyEngine.extract(pdf);
        String filename = file.getOriginalFilename() == null ? "document.pdf" : file.getOriginalFilename();
        var doc = documentStore.save(owner, filename, extracted.text(), extracted.pageCount(), extracted.needsOcr(),
                pdf);
        return ResponseEntity.status(HttpStatus.CREATED).body(DocumentSummary.of(doc));
    }

    @GetMapping(value = DOCUMENTS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<DocumentSummary> list(
            @CookieValue(value = AuthController.SESSION_ID_COOKIE_NAME, required = false) String sessionId) {
        return documentStore.list(currentUser.username(sessionId));
    }

    @DeleteMapping(DOCUMENTS_PATH + "/{id}")
    public ResponseEntity<Void> delete(
            @CookieValue(value = AuthController.SESSION_ID_COOKIE_NAME, required = false) String sessionId,
            @PathVariable String id) {
        boolean deleted = documentStore.delete(currentUser.username(sessionId), id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
