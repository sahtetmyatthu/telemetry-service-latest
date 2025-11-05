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
 * Manages the lifecycle of MAVLink listeners.
 * Handles starting, stopping, and monitoring listeners.
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

        // Start health check every 10 seconds
        healthCheckExecutor.scheduleAtFixedRate(
                this::checkListenerHealth, 3, 3, TimeUnit.SECONDS);
    }

    /**
     * Starts a listener on the specified port if not already active.
     *
     * @param port Port to start listening on
     * @return true if listener was started, false if already active
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
     * Stops the listener on the specified port.
     *
     * @param port Port to stop listening on
     */
    public void stopListener(int port) {
        ListenerState state = activeListeners.remove(port);
        if (state != null) {
            state.stop();
            log.info("Stopped listener on port {}", port);
        }
    }

    /**
     * Checks if a listener is active on the port.
     *
     * @param port Port to check
     * @return true if listener is active
     */
    public boolean isListenerActive(int port) {
        ListenerState state = activeListeners.get(port);
        return state != null && state.isActive();
    }

    /**
     * Gets all active listener ports.
     *
     * @return Set of active port numbers
     */
    public Set<Integer> getActivePorts() {
        return new HashSet<>(activeListeners.keySet());
    }

    /**
     * Periodically checks listener health and removes dead ones.
     */
    private void checkListenerHealth() {
        activeListeners.entrySet().removeIf(entry -> {
            ListenerState state = entry.getValue();
            if (!state.isActive()) {
                log.warn("Removing dead listener on port {}", entry.getKey());
                return true;
            }
            return false;
        });
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
     * Internal state tracker for a listener.
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
