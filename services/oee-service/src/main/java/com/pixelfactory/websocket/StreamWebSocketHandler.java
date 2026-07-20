package com.pixelfactory.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 단순 브로드캐스트 채널. 모든 접속 세션에 {"type": ..., "data": ...} JSON을 보낸다.
 * type: "event" (FactoryEvent 실시간), "oee" (주기적 OEE 스냅샷).
 */
@Component
public class StreamWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(StreamWebSocketHandler.class);

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final ObjectMapper objectMapper;

    public StreamWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("WebSocket connected: {} (total {})", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket closed: {} (total {})", session.getId(), sessions.size());
    }

    public boolean hasActiveSessions() {
        return !sessions.isEmpty();
    }

    public void broadcast(String type, Object data) {
        if (sessions.isEmpty()) {
            return;
        }

        // LinkedHashMap: "type"이 항상 먼저 오도록 키 순서를 고정한다 (클라이언트 파싱 편의).
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", type);
        envelope.put("data", data);

        TextMessage message;
        try {
            message = new TextMessage(objectMapper.writeValueAsString(envelope));
        } catch (Exception e) {
            log.error("Failed to serialize WebSocket message of type {}", type, e);
            return;
        }

        for (WebSocketSession session : sessions) {
            try {
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(message);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to send to WebSocket session {}. Dropping session.", session.getId(), e);
                sessions.remove(session);
            }
        }
    }
}
