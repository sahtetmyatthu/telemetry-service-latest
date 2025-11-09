package org.mdt.telemetry_service.listener;

import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.mdt.telemetry_service.utils.Constants;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * FIXED: Reduced health check frequency
 */
@Component
@Slf4j
public class ListenerManager {

    private final MavlinkListener mavlinkListener;
    private final Map<Integer, ListenerState> activeListeners;
    private final ScheduledExecutorService healthCheckExecutor;

    public ListenerManager(MavlinkListener mavlinkListener) {
        this.mavlinkListener = mavlinkListener;
        this.activeListeners = new ConcurrentHashMap<>();
        this.healthCheckExecutor = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, Constants.THREAD_HEALTH_CHECK));

        // FIXED: Reduced health check from every 3 seconds to every 30 seconds
        // Dead listeners will be cleaned up eventually, no need to check so often
        healthCheckExecutor.scheduleAtFixedRate(
                this::checkListenerHealth, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * Starts a listener on the specified port if not already active
     */
    public boolean startListener(int port) {
        if (isListenerActive(port)) {
            log.debug("Listener already active on port {}", port);
            return false;
        }

        try {
            Future<?> future = mavlinkListener.listenOnPort(port);
            ListenerState state = new ListenerState(port, future);
            activeListeners.put(port, state);

            log.info("Started listener on port {}", port);
            return true;

        } catch (Exception e) {
            log.error("Failed to start listener on port {}", port, e);
            return false;
        }
    }

    /**
     * Stops the listener on the specified port
     */
    public void stopListener(int port) {
        ListenerState state = activeListeners.remove(port);
        if (state != null) {
            state.stop();
            log.info("Stopped listener on port {}", port);
        }
    }

    /**
     * Checks if a listener is active on the port
     */
    public boolean isListenerActive(int port) {
        ListenerState state = activeListeners.get(port);
        return state != null && state.isActive();
    }

    /**
     * Gets all active listener ports
     */
    public Set<Integer> getActivePorts() {
        return new HashSet<>(activeListeners.keySet());
    }

    /**
     * Periodically checks listener health and removes dead ones
     */
    private void checkListenerHealth() {
        int removed = 0;
        for (Map.Entry<Integer, ListenerState> entry : activeListeners.entrySet()) {
            ListenerState state = entry.getValue();
            if (!state.isActive()) {
                activeListeners.remove(entry.getKey());
                log.warn("Removed dead listener on port {}", entry.getKey());
                removed++;
            }
        }
        if (removed > 0) {
            log.info("Health check removed {} dead listeners, {} active",
                    removed, activeListeners.size());
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down listener manager");
        healthCheckExecutor.shutdown();

        activeListeners.values().forEach(ListenerState::stop);
        activeListeners.clear();

        try {
            if (!healthCheckExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                healthCheckExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            healthCheckExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Internal state tracker for a listener
     */
    @Getter
    private static class ListenerState {
        private final int port;
        private final Future<?> future;
        private final long startTime;

        ListenerState(int port, Future<?> future) {
            this.port = port;
            this.future = future;
            this.startTime = System.currentTimeMillis();
        }

        boolean isActive() {
            return !future.isDone() && !future.isCancelled();
        }

        void stop() {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
    }
}