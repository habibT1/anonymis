package com.inf5190.chat.websocket;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Classe qui gère une connexion websocket.
 */
@Component
public class WebSocketHandler extends TextWebSocketHandler {

    private final WebSocketManager webSocketManager;

    public WebSocketHandler(WebSocketManager webSocketManager) {
        this.webSocketManager = webSocketManager;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        this.webSocketManager.addSession(session);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session,
            @NonNull CloseStatus status) throws Exception {
        this.webSocketManager.removeSession(session);
    }

}
