package com.inf5190.chat.status;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.ExecutionException;

import com.inf5190.chat.auth.session.SessionData;
import com.inf5190.chat.auth.session.SessionManager;
import com.inf5190.chat.status.model.UserStatus;
import com.inf5190.chat.status.model.UpdateStatusRequest;
import com.inf5190.chat.status.repository.UserStatusRepository;
import com.inf5190.chat.websocket.WebSocketManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Contrôleur pour gérer les statuts des utilisateurs.
 */
@RestController
public class UserStatusController {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserStatusController.class);

    public static final String STATUS_PATH = "/status";

    private final UserStatusRepository userStatusRepository;
    private final SessionManager sessionManager;
    private final WebSocketManager webSocketManager;

    public UserStatusController(
            UserStatusRepository userStatusRepository,
            SessionManager sessionManager,
            WebSocketManager webSocketManager
    ) {
        this.userStatusRepository = userStatusRepository;
        this.sessionManager = sessionManager;
        this.webSocketManager = webSocketManager;
    }

    /**
     * Récupère tous les statuts des utilisateurs.
     */
    @GetMapping(
            value = STATUS_PATH,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<List<UserStatus>> getAllStatuses()
            throws InterruptedException, ExecutionException {

        try {
            List<UserStatus> statuses = userStatusRepository.getAllStatuses();
            return ResponseEntity.ok(statuses);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.warn("Erreur inattendue lors de la récupération des statuts.", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unexpected error on get statuses.");
        }
    }

    /**
     * Met à jour le statut de l'utilisateur connecté.
     */
    @PutMapping(
            value = STATUS_PATH,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<UserStatus> updateStatus(
            @CookieValue(value = "__session", required = false) String sessionId,
            @RequestBody UpdateStatusRequest request
    ) throws InterruptedException, ExecutionException {

        try {
            if (sessionId == null || sessionId.isBlank()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }

            SessionData sessionData = sessionManager.getSession(sessionId);
            if (sessionData == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }

            String status = request.status();
            if (status == null || status.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
            }

            if (!status.equals("online") && !status.equals("offline") && !status.equals("away")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Status must be online, offline, or away");
            }

            UserStatus updated = userStatusRepository.updateUserStatus(
                    sessionData.username(),
                    status
            );

            webSocketManager.notifySessions();

            return ResponseEntity.ok(updated);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.warn("Erreur inattendue lors de la mise à jour du statut.", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unexpected error on update status.");
        }
    }
}
