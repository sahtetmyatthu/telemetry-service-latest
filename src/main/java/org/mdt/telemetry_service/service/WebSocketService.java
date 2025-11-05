package org.mdt.telemetry_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Handles WebSocket connections and broadcasts telemetry data.
 */
@Component
@Slf4j
@RequiredArgsConstructor  // Lombok will create constructor with final fields
public class WebSocketService extends TextWebSocketHandler {

    private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final Map<String, String> sessionPortMap = new ConcurrentHashMap<>();
    private final Map<String, Object> sessionLocks = new ConcurrentHashMap<>();

    // Inject Spring's pre-configured ObjectMapper
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        sessions.add(session);
        String port = extractPortFromUri(Objects.requireNonNull(session.getUri()).toString());
        if (port != null) {
            sessionPortMap.put(session.getId(), port);
        }
        log.info("WebSocket connected: {}, port: {}", session.getId(), port != null ? port : "all");
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        sessionPortMap.remove(session.getId());
        sessionLocks.remove(session.getId());
        log.info("WebSocket disconnected: {}", session.getId());
    }

    public void broadcast(Map<String, Object> data) {
        if (data == null || !data.containsKey("drones")) {
            log.warn("Invalid data: null or missing 'drones' key");
            return;
        }

        List<?> dronesList = (List<?>) data.get("drones");

        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                continue;
            }

            Object lock = sessionLocks.computeIfAbsent(session.getId(), k -> new Object());

            synchronized (lock) {
                try {
                    String sessionId = session.getId();
                    String port = sessionPortMap.get(sessionId);

                    String jsonData;

                    if (port != null) {
                        Object portData = dronesList.stream()
                                .filter(drone -> {
                                    if (drone instanceof Map<?, ?> droneMap) {
                                        return String.valueOf(droneMap.get("port")).equals(port);
                                    }
                                    return false;
                                })
                                .findFirst()
                                .orElse(null);

                        if (portData != null) {
                            jsonData = objectMapper.writeValueAsString(
                                    Map.of("drones", List.of(portData)));
                            log.trace("Sent port-specific telemetry for port {} to session {}",
                                    port, sessionId);
                        } else {
                            continue;
                        }
                    } else {
                        jsonData = objectMapper.writeValueAsString(data);
                        log.trace("Sent all telemetry data to session {}", sessionId);
                    }

                    session.sendMessage(new TextMessage(jsonData));

                } catch (IOException e) {
                    log.error("Error sending message to session {}", session.getId(), e);
                }
            }
        }
    }

    private String extractPortFromUri(String uri) {
        if (uri != null) {
            if (uri.endsWith("/telemetry")) {
                return null;
            }
            if (uri.contains("/telemetry/")) {
                String[] parts = uri.split("/telemetry/");
                if (parts.length > 1 && !parts[1].isEmpty()) {
                    return parts[1];
                }
            }
        }
        return null;
    }
}