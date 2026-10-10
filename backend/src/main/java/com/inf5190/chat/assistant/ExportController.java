package com.inf5190.chat.assistant;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.inf5190.chat.auth.AuthController;
import com.inf5190.chat.auth.session.CurrentUser;
import com.inf5190.chat.documents.DocumentController;
import com.inf5190.chat.documents.DocumentStore;
import com.inf5190.chat.documents.DocumentStore.StoredDocument;
import com.inf5190.chat.privacy.PrivacyEngineClient;
import com.inf5190.chat.privacy.PrivacyEngineClient.RedactionSpan;
import com.inf5190.chat.privacy.TokenVault;
import com.inf5190.chat.privacy.model.DetectedEntity;
import com.inf5190.chat.privacy.model.DetectionOptions;

/**
 * Exports PDF, générés en local par le privacy-engine. Rien ne passe par l'IA.
 */
@RestController
public class ExportController {
    public static final String EXPORT_PATH = "/export/pdf";

    private final DocumentStore documents;
    private final ConversationStore conversations;
    private final PrivacyEngineClient privacyEngine;
    private final TokenVault tokenVault;
    private final CurrentUser currentUser;

    public ExportController(DocumentStore documents, ConversationStore conversations,
            PrivacyEngineClient privacyEngine, TokenVault tokenVault, CurrentUser currentUser) {
        this.documents = documents;
        this.conversations = conversations;
        this.privacyEngine = privacyEngine;
        this.tokenVault = tokenVault;
        this.currentUser = currentUser;
    }

    /**
     * PDF caviardé. Avec conversationId, les jetons sont les mêmes que dans la conversation
     * (le [PERSONNE_1] du PDF est le [PERSONNE_1] de la Vue IA).
     */
    @GetMapping(DocumentController.DOCUMENTS_PATH + "/{id}/redacted")
    public ResponseEntity<byte[]> redacted(
            @CookieValue(value = AuthController.SESSION_ID_COOKIE_NAME, required = false) String sessionId,
            @PathVariable String id,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false, defaultValue = "standard") String level) {
        String owner = currentUser.username(sessionId);
        StoredDocument doc = documents.find(owner, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable"));
        if (doc.pdf().length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF original indisponible");
        }

        var conv = conversationId == null ? null : conversations.find(owner, conversationId).orElse(null);
        String vaultId = conv != null ? conv.id() : "export-" + UUID.randomUUID();
        DetectionOptions options = conv != null ? conv.options() : new DetectionOptions(level, null, null);
        try {
            List<DetectedEntity> entities = privacyEngine.detect(doc.text(), options);
            List<String> tokens = tokenVault.tokensFor(vaultId, entities);
            List<RedactionSpan> spans = new ArrayList<>();
            for (int i = 0; i < entities.size(); i++) {
                spans.add(new RedactionSpan(entities.get(i).start(), entities.get(i).end(), tokens.get(i)));
            }
            byte[] pdf = privacyEngine.redact(doc.pdf(), spans);
            return pdfResponse(pdf, baseName(doc.filename()) + "-caviarde.pdf");
        } finally {
            if (conv == null) {
                tokenVault.forget(vaultId);
            }
        }
    }

    public record ExportRequest(String title, String text) {
    }

    /** Une réponse de l'assistant (déjà restaurée côté client) mise en PDF. */
    @PostMapping(value = EXPORT_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> exportPdf(
            @CookieValue(value = AuthController.SESSION_ID_COOKIE_NAME, required = false) String sessionId,
            @RequestBody ExportRequest request) {
        currentUser.username(sessionId);
        if (request == null || request.text() == null || request.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Texte vide");
        }
        String title = request.title() == null || request.title().isBlank() ? "Anonymis" : request.title();
        return pdfResponse(privacyEngine.render(title, request.text()), "anonymis-reponse.pdf");
    }

    private static String baseName(String filename) {
        return filename.toLowerCase().endsWith(".pdf") ? filename.substring(0, filename.length() - 4) : filename;
    }

    private static ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(pdf);
    }
}
