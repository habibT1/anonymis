package com.inf5190.chat.auth.session;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Retrouve l'utilisateur connecté à partir du cookie de session.
 */
@Component
public class CurrentUser {
    private final SessionManager sessionManager;

    public CurrentUser(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public String username(String sessionId) {
        SessionData data = sessionId == null || sessionId.isBlank() ? null : sessionManager.getSession(sessionId);
        if (data == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return data.username();
    }
}
