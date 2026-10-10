package com.inf5190.chat.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.inf5190.chat.auth.model.LoginRequest;
import com.inf5190.chat.auth.model.LoginResponse;
import com.inf5190.chat.auth.session.SessionManager;
import com.inf5190.chat.auth.session.SessionData;
import com.inf5190.chat.auth.repository.UserAccountRepository;
import com.inf5190.chat.auth.repository.FirestoreUserAccount;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contrôleur qui gère l'API de login et logout.
 */
@RestController
public class AuthController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    public static final String AUTH_LOGIN_PATH = "/auth/login";
    public static final String AUTH_LOGOUT_PATH = "/auth/logout";
    public static final String SESSION_ID_COOKIE_NAME = "__session";

    private final SessionManager sessionManager;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.inf5190.chat.status.repository.UserStatusRepository userStatusRepository;
    private final com.inf5190.chat.websocket.WebSocketManager webSocketManager;

    public AuthController(SessionManager sessionManager,
                          UserAccountRepository userAccountRepository,
                          PasswordEncoder passwordEncoder,
                          com.inf5190.chat.status.repository.UserStatusRepository userStatusRepository,
                          com.inf5190.chat.websocket.WebSocketManager webSocketManager) {
        this.sessionManager = sessionManager;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.userStatusRepository = userStatusRepository;
        this.webSocketManager = webSocketManager;
    }

    @PostMapping(AUTH_LOGIN_PATH)
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest loginRequest)
            throws InterruptedException, ExecutionException {

        try {
            final String username = loginRequest.username();
            final String password = loginRequest.password();


            FirestoreUserAccount account = userAccountRepository.getUserAccount(username);

            if (account != null) {
                if (!passwordEncoder.matches(password, account.getEncodedPassword())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN);
                }
            }


            if (account == null) {
                FirestoreUserAccount newAccount =
                        new FirestoreUserAccount(username, passwordEncoder.encode(password));
                userAccountRepository.createUserAccount(newAccount);
            }


            SessionData session = new SessionData(username);
            String sid = sessionManager.addSession(session);


            ResponseCookie cookie = ResponseCookie.from(SESSION_ID_COOKIE_NAME, sid)
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .maxAge(Duration.ofHours(24))
                    .sameSite("Lax")
                    .build();


            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(new LoginResponse(username));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.warn("Erreur inattendue lors de la connexion.", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unexpected error on login.");
        }
    }

    @PostMapping(AUTH_LOGOUT_PATH)
    public ResponseEntity<Void> logout(
            @CookieValue(value = SESSION_ID_COOKIE_NAME, required = false) String sid)
            throws InterruptedException, ExecutionException {

        if (sid != null && !sid.isBlank()) {
            SessionData session = sessionManager.getSession(sid);
            if (session != null) {
                // Mettre le statut de l'utilisateur à offline
                try {
                    userStatusRepository.updateUserStatus(session.username(), "offline");
                    webSocketManager.notifySessions();
                } catch (Exception e) {
                    LOGGER.warn("Erreur lors de la mise à jour du statut lors du logout", e);
                }
            }
            sessionManager.removeSession(sid);
        }

        ResponseCookie delete = ResponseCookie.from(SESSION_ID_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, delete.toString())
                .build();
    }
}