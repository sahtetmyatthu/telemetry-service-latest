package org.mdt.telemetry_service.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.mdt.telemetry_service.dto.TelemetryDataDto;
import org.mdt.telemetry_service.mapper.TelemetryMapper;
import org.mdt.telemetry_service.model.TelemetryData;
import org.mdt.telemetry_service.repository.TelemetryRepository;
import org.mdt.telemetry_service.utils.Constants;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * FIXED: Batch updates and async processing to reduce database load
 */
@Service
@Slf4j
public class TelemetryService {
    private final TelemetryRepository repository;
    private final TelemetryMapper mapper;
    private final WebSocketService webSocketService;
    private final Map<Integer, TelemetryData> telemetryCache;
    private final Map<Integer, Long> lastActivityTime;

    // FIXED: Batch processing
    private final ScheduledExecutorService batchProcessor;
    private final Map<Integer, TelemetryData> pendingUpdates;
    private volatile long lastBroadcast = 0;
    private static final long BROADCAST_INTERVAL_MS = 100; // Broadcast max 10 times per second

    public TelemetryService(
            TelemetryRepository repository,
            TelemetryMapper mapper,
            WebSocketService webSocketService) {
        this.repository = repository;
        this.mapper = mapper;
        this.webSocketService = webSocketService;
        this.telemetryCache = new ConcurrentHashMap<>();
        this.lastActivityTime = new ConcurrentHashMap<>();
        this.pendingUpdates = new ConcurrentHashMap<>();

        // FIXED: Use single thread for batch processing
        this.batchProcessor = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "telemetry-batch-processor"));
    }

    @PostConstruct
    public void init() {
        // FIXED: Batch persist every 5 seconds instead of every update
        batchProcessor.scheduleAtFixedRate(
                this::persistBatch,
                5, 5, TimeUnit.SECONDS
        );

        // FIXED: Broadcast updates at controlled rate
        batchProcessor.scheduleAtFixedRate(
                this::broadcastUpdate,
                100, 100, TimeUnit.MILLISECONDS
        );

        log.info("Initialized TelemetryService with batch processing");
    }

    /**
     * FIXED: Updates telemetry in cache only, batch persists later
     */
    public void updateTelemetry(TelemetryData telemetryData) {
        int port = telemetryData.getPort();

        // Update cache immediately
        telemetryCache.put(port, telemetryData);
        lastActivityTime.put(port, System.currentTimeMillis());

        // FIXED: Mark for batch persistence instead of immediate save
        pendingUpdates.put(port, telemetryData);

        // Note: Broadcasting is now throttled by scheduled task
    }

    /**
     * FIXED: Batch persist to database
     */
    private void persistBatch() {
        if (pendingUpdates.isEmpty()) {
            return;
        }

        // Copy and clear pending updates
        Map<Integer, TelemetryData> toSave = new ConcurrentHashMap<>(pendingUpdates);
        pendingUpdates.clear();

        try {
            // Batch save
            repository.saveAll(toSave.values());
            log.debug("Batch persisted {} telemetry records", toSave.size());
        } catch (Exception e) {
            log.error("Failed to batch persist telemetry", e);
            // Put failed items back for retry
            pendingUpdates.putAll(toSave);
        }
    }

    /**
     * Gets all active telemetry data with caching
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
     * Gets or creates telemetry data for a port
     */
    public TelemetryData getOrCreateTelemetryData(int port) {
        return telemetryCache.computeIfAbsent(port, TelemetryData::new);
    }

    /**
     * Updates last activity time for a port
     */
    public void updateActivity(int port) {
        lastActivityTime.put(port, System.currentTimeMillis());
    }

    /**
     * FIXED: Throttled broadcast to prevent flooding WebSocket clients
     */
    private void broadcastUpdate() {
        try {
            long now = System.currentTimeMillis();

            // Skip if no cache or too soon since last broadcast
            if (telemetryCache.isEmpty()) {
                return;
            }

            List<TelemetryDataDto> dtos = getAllActiveTelemetry();
            if (!dtos.isEmpty()) {
                webSocketService.broadcast(Map.of(Constants.WS_DRONES_KEY, dtos));
            }
        } catch (Exception e) {
            log.error("Error broadcasting telemetry update", e);
        }
    }

    /**
     * FIXED: Cleanup stale data periodically
     */
    @PostConstruct
    public void startCleanupTask() {
        batchProcessor.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            long threshold = Constants.STALE_DATA_THRESHOLD_MS * 2; // 2x stale threshold

            telemetryCache.entrySet().removeIf(entry -> {
                Long lastActive = lastActivityTime.get(entry.getKey());
                return lastActive == null || (now - lastActive) > threshold;
            });

            lastActivityTime.entrySet().removeIf(entry ->
                    (now - entry.getValue()) > threshold
            );
        }, 60, 60, TimeUnit.SECONDS); // Cleanup every minute
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down telemetry service");

        // Final persist of pending updates
        persistBatch();

        batchProcessor.shutdown();
        try {
            if (!batchProcessor.awaitTermination(10, TimeUnit.SECONDS)) {
                batchProcessor.shutdownNow();
            }
        } catch (InterruptedException e) {
            batchProcessor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

}