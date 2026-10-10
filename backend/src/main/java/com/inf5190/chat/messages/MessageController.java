package com.inf5190.chat.messages;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.ExecutionException;

import com.inf5190.chat.auth.session.SessionData;
import com.inf5190.chat.auth.session.SessionManager;
import com.inf5190.chat.messages.model.Message;
import com.inf5190.chat.messages.model.NewMessageRequest;
import com.inf5190.chat.messages.repository.MessageRepository;
import com.inf5190.chat.websocket.WebSocketManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
public class MessageController {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageController.class);

    public static final String MESSAGES_PATH = "/messages";

    private final MessageRepository messageRepository;
    private final WebSocketManager webSocketManager;
    private final SessionManager sessionManager;

    public MessageController(
            MessageRepository messageRepository,
            WebSocketManager webSocketManager,
            SessionManager sessionManager
    ) {
        this.messageRepository = messageRepository;
        this.webSocketManager = webSocketManager;
        this.sessionManager = sessionManager;
    }


    @GetMapping(
            value = MESSAGES_PATH,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<List<Message>> list(
            @RequestParam(name = "fromId", required = false) String fromId
    ) throws InterruptedException, ExecutionException {

        try {
            List<Message> result = messageRepository.getMessages(fromId);

            // Si fromId est fourni et que la liste est vide, cela signifie que le document n'existe pas
            if (fromId != null && !fromId.isBlank() && result.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Message with fromId not found");
            }

            return ResponseEntity.ok(result);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.warn("Erreur inattendue lors de la récupération des messages.", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unexpected error on get messages.");
        }
    }


    @PostMapping(
            value = MESSAGES_PATH,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Message> create(
            @CookieValue(value = "__session", required = false) String sessionId,
            @RequestBody NewMessageRequest body
    ) throws InterruptedException, ExecutionException {

        try {
            if (sessionId == null || sessionId.isBlank()) {
                return ResponseEntity.status(403).build();
            }

            SessionData sessionData = sessionManager.getSession(sessionId);
            if (sessionData == null) {
                return ResponseEntity.status(403).build();
            }


            boolean hasText = (body != null
                    && body.text() != null
                    && !body.text().trim().isEmpty());

            boolean hasImage = (body != null && body.imageData() != null);

            if (!hasText && !hasImage) {
                return ResponseEntity.badRequest().build();
            }

            NewMessageRequest safeRequest = new NewMessageRequest(
                    hasText ? body.text().trim() : "",
                    sessionData.username(),
                    body.imageData()
            );


            Message saved = messageRepository.createMessage(safeRequest);

            webSocketManager.notifySessions();

            return ResponseEntity.ok(saved);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.warn("Erreur inattendue lors de la création d'un message.", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unexpected error on create message.");
        }
    }
}