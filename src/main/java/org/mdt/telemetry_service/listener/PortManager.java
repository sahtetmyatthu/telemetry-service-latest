package org.mdt.telemetry_service.listener;

import lombok.extern.slf4j.Slf4j;
import org.mdt.telemetry_service.config.TelemetryProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple manager for the list of ports to scan.
 * Thread-safe, focused only on managing the port list.
 */
@Component
@Slf4j
public class PortManager {

    private final Set<Integer> portsToScan;
    private final TelemetryProperties properties;

    public PortManager(TelemetryProperties properties) {
        this.properties = properties;
        this.portsToScan = ConcurrentHashMap.newKeySet();
        initializeDefaultPorts();
    }

    // should import ports list here?
    private void initializeDefaultPorts() {
        int min = properties.getPortRange().getMin();
        int max = properties.getPortRange().getMax();

        for (int port = min; port <= max; port++) {
            portsToScan.add(port);
        }

        log.info("Initialized port manager with {} ports ({}-{})",
                portsToScan.size(), min, max);
    }

    /**
     * Adds a port to the scan list.
     *
     * @param port Port to add
     * @return true if port was added
     */
    public boolean addPort(int port) {
        if (!isValidPort(port)) {
            log.warn("Invalid port: {}", port);
            return false;
        }

        if (portsToScan.size() >= properties.getMaxPorts()) {
            log.warn("Cannot add port {}: max limit reached", port);
            return false;
        }

        boolean added = portsToScan.add(port);
        if (added) {
            log.info("Added port {} to scan list", port);
        }
        return added;
    }

    /**
     * Removes a port from the scan list.
     *
     * @param port Port to remove
     * @return true if port was removed
     */
    public boolean removePort(int port) {
        boolean removed = portsToScan.remove(port);
        if (removed) {
            log.info("Removed port {} from scan list", port);
        }
        return removed;
    }

    /**
     * Gets immutable copy of ports to scan.
     *
     * @return List of port numbers
     */
    public List<Integer> getPortsToScan() {
        return new ArrayList<>(portsToScan);
    }

    /**
     * Gets the count of ports being scanned.
     *
     * @return Number of ports
     */
    public int getPortCount() {
        return portsToScan.size();
    }

    private boolean isValidPort(int port) {
        return port >= properties.getPortRange().getMin()
                && port <= properties.getPortRange().getMax();
    }
}
