package com.inf5190.chat.websocket;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Service qui gère les sessions websocket.
 */
@Service
public class WebSocketManager {
    private final Logger logger = LoggerFactory.getLogger(WebSocketManager.class);
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    public void addSession(WebSocketSession session) {
        if (session != null) {
            this.sessions.add(session);
        }
    }

    public void removeSession(WebSocketSession session) {
        if (session != null) {
            this.sessions.remove(session);
        }
    }

    public void notifySessions() {
        List<WebSocketSession> toRemove = new ArrayList<>();

        for (WebSocketSession s : sessions) {
            if (s == null || !s.isOpen()) {
                toRemove.add(s);
                continue;
            }
            try {
                s.sendMessage(new TextMessage("notif"));
            } catch (Exception e) {
                logger.info("Could not notify session id={} — removing it.", s.getId(), e);
                toRemove.add(s);
            }
        }

        if (!toRemove.isEmpty()) {
            sessions.removeAll(toRemove);
        }
    }
}
