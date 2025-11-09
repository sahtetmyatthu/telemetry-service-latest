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
 * FIXED: Reduced scanning frequency to lower CPU usage
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

        // FIXED: Reduced scan frequency from 1 second to 5 seconds
        // This dramatically reduces CPU usage
        scheduler.scheduleWithFixedDelay(
                this::scanCycle,
                0,
                5,  // Was 1 second, now 5 seconds
                TimeUnit.SECONDS
        );
    }

    /**
     * Single scan cycle - simple and clear logic
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
     * Returns ports that should be scanned
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