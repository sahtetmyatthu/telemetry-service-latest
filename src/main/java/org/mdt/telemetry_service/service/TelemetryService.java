package org.mdt.telemetry_service.service;


import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.mdt.telemetry_service.dto.TelemetryDataDto;
import org.mdt.telemetry_service.mapper.TelemetryMapper;
import org.mdt.telemetry_service.model.TelemetryData;
import org.mdt.telemetry_service.repository.TelemetryRepository;
import org.mdt.telemetry_service.utils.Constants;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service layer for telemetry data operations.
 * Manages in-memory cache and persistence.
 */
@Service
@Slf4j
public class TelemetryService {
    private final TelemetryRepository repository;
    private final TelemetryMapper mapper;
    private final WebSocketService webSocketService;
    private final Map<Integer, TelemetryData> telemetryCache;
    private final Map<Integer, Long> lastActivityTime;

    public TelemetryService(
            TelemetryRepository repository,
            TelemetryMapper mapper,
            WebSocketService webSocketService) {
        this.repository = repository;
        this.mapper = mapper;
        this.webSocketService = webSocketService;
        this.telemetryCache = new ConcurrentHashMap<>();
        this.lastActivityTime = new ConcurrentHashMap<>();
    }

    /**
     * Updates telemetry data in cache and persistence.
     */
    @Transactional
    public void updateTelemetry(TelemetryData telemetryData) {
        int port = telemetryData.getPort();

        // Update cache
        telemetryCache.put(port, telemetryData);
        lastActivityTime.put(port, System.currentTimeMillis());

        // Persist asynchronously
        try {
            repository.save(telemetryData);
        } catch (Exception e) {
            log.error("Failed to persist telemetry for port {}", port, e);
        }

        // Broadcast to WebSocket clients
        broadcastUpdate();
    }


    /**
     * Gets all active telemetry data.
     * Only includes ports with recent activity.
     */
    public List<TelemetryDataDto> getAllActiveTelemetry() {
        long now = System.currentTimeMillis();

        return telemetryCache.entrySet().stream()
                .filter(entry -> {
                    Long lastActive = lastActivityTime.get(entry.getKey());
                    return lastActive != null &&
                            (now - lastActive) <= Constants.STALE_DATA_THRESHOLD_MS;
                })
                .map(entry -> mapper.toDto(entry.getValue()))
                .collect(Collectors.toList());
    }



    /**
     * Gets or creates telemetry data for a port.
     */
    public TelemetryData getOrCreateTelemetryData(int port) {
        return telemetryCache.computeIfAbsent(port, TelemetryData::new);
    }


    /**
     * Updates last activity time for a port.
     */
    public void updateActivity(int port) {
        lastActivityTime.put(port, System.currentTimeMillis());
    }

    /**
     * Broadcasts telemetry update to all WebSocket clients.
     */
    private void broadcastUpdate() {
        try {
            List<TelemetryDataDto> dtos = getAllActiveTelemetry();
            webSocketService.broadcast(Map.of(Constants.WS_DRONES_KEY, dtos));
        } catch (Exception e) {
            log.error("Error broadcasting telemetry update", e);
        }
    }

}
