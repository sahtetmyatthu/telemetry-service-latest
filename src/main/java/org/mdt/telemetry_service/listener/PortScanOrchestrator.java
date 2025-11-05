package org.mdt.telemetry_service.listener;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.mdt.telemetry_service.utils.Constants;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Orchestrates port scanning and listener startup.
 * Simple coordinator that replaces the complex PortScanner.
 */
@Component
@Slf4j
public class PortScanOrchestrator {

    private final PortDetector portDetector;
    private final ListenerManager listenerManager;
    private final PortManager portManager;
    private final ScheduledExecutorService scheduler;

    public PortScanOrchestrator(
            PortDetector portDetector,
            ListenerManager listenerManager,
            PortManager portManager) {
        this.portDetector = portDetector;
        this.listenerManager = listenerManager;
        this.portManager = portManager;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, Constants.THREAD_PORT_SCANNER));


    }

    @PostConstruct
    public void start() {
        log.info("Starting port scan orchestrator");

        // Run scan every 1 second
        scheduler.scheduleWithFixedDelay(
                this::scanCycle,
                0,
                1,
                TimeUnit.SECONDS
        );
    }

    /**
     * Single scan cycle - simple and clear logic!
     */
    private void scanCycle() {
        try {
            // 1. Get ports that need scanning
            List<Integer> portsToScan = getPortsNeedingScanning();

            if (portsToScan.isEmpty()) {
                log.trace("No ports to scan");
                return;
            }

            log.debug("Scanning {} ports", portsToScan.size());

            // 2. Scan them
            List<PortScanResult> results = portDetector.scanPorts(portsToScan);

            // 3. Start listeners for ports with data
            results.stream()
                    .filter(PortScanResult::hasData)
                    .forEach(result -> {
                        boolean started = listenerManager.startListener(result.getPort());
                        if (started) {
                            log.info("Activated listener for port {} after detecting data",
                                    result.getPort());
                        }
                    });

        } catch (Exception e) {
            log.error("Error in scan cycle", e);
        }
    }

    /**
     * Returns ports that should be scanned:
     * - Ports in the scan list
     * - That DON'T already have active listeners
     */
    private List<Integer> getPortsNeedingScanning() {
        Set<Integer> activePorts = listenerManager.getActivePorts();

        return portManager.getPortsToScan().stream()
                .filter(port -> !activePorts.contains(port))
                .collect(Collectors.toList());
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down port scan orchestrator");
        scheduler.shutdown();

        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
