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
 * FIXED: Optimized WebSocket with caching to reduce JSON serialization
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketService extends TextWebSocketHandler {

    private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final Map<String, String> sessionPortMap = new ConcurrentHashMap<>();
    private final Map<String, Object> sessionLocks = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    // FIXED: Cache serialized JSON to avoid repeated serialization
    private volatile String cachedAllDronesJson = null;
    private volatile long lastCacheTime = 0;
    private static final long CACHE_TTL_MS = 100; // Cache for 100ms
    private final Map<String, String> portSpecificCache = new ConcurrentHashMap<>();

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

    /**
     * FIXED: Broadcast with JSON caching to reduce CPU usage
     */
    public void broadcast(Map<String, Object> data) {
        if (data == null || !data.containsKey("drones")) {
            log.warn("Invalid data: null or missing 'drones' key");
            return;
        }

        List<?> dronesList = (List<?>) data.get("drones");
        if (dronesList.isEmpty()) {
            return; // No data to send
        }

        long now = System.currentTimeMillis();

        // FIXED: Check if we can use cached JSON
        boolean cacheValid = (now - lastCacheTime) < CACHE_TTL_MS;

        if (!cacheValid) {
            // Invalidate all caches
            cachedAllDronesJson = null;
            portSpecificCache.clear();
            lastCacheTime = now;
        }

        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                continue;
            }

            try {
                String sessionId = session.getId();
                String port = sessionPortMap.get(sessionId);
                String jsonData;

                if (port != null) {
                    // Port-specific session
                    jsonData = portSpecificCache.computeIfAbsent(port, p -> {
                        Object portData = dronesList.stream()
                                .filter(drone -> {
                                    if (drone instanceof Map<?, ?> droneMap) {
                                        return String.valueOf(droneMap.get("port")).equals(p);
                                    }
                                    return false;
                                })
                                .findFirst()
                                .orElse(null);

                        if (portData != null) {
                            try {
                                return objectMapper.writeValueAsString(
                                        Map.of("drones", List.of(portData)));
                            } catch (IOException e) {
                                log.error("Error serializing port data", e);
                                return null;
                            }
                        }
                        return null;
                    });

                    if (jsonData == null) {
                        continue; // No data for this port
                    }
                } else {
                    // All drones session - use cached JSON
                    if (cachedAllDronesJson == null) {
                        cachedAllDronesJson = objectMapper.writeValueAsString(data);
                    }
                    jsonData = cachedAllDronesJson;
                }

                // Send with per-session locking
                Object lock = sessionLocks.computeIfAbsent(sessionId, k -> new Object());
                synchronized (lock) {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(jsonData));
                    }
                }

            } catch (IOException e) {
                log.debug("Error sending message to session {}: {}", session.getId(), e.getMessage());
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