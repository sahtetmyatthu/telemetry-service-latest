package org.mdt.telemetry_service.listener;

import lombok.extern.slf4j.Slf4j;
import org.mdt.telemetry_service.config.TelemetryProperties;
import org.mdt.telemetry_service.utils.Constants;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * FIXED: Added intelligent port scanning with backoff and parallel limit
 */
@Component
@Slf4j
public class PortDetector {

    private final TelemetryProperties properties;
    private final ExecutorService scanExecutor;

    // FIXED: Track ports that repeatedly fail to avoid wasting CPU
    private final ConcurrentHashMap<Integer, Integer> failureCount = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> lastScanTime = new ConcurrentHashMap<>();
    private static final int MAX_FAILURES = 5;
    private static final long BACKOFF_TIME_MS = 60000; // 1 minute

    public PortDetector(TelemetryProperties properties) {
        this.properties = properties;

        // FIXED: Limit parallel scans to prevent CPU spike
        this.scanExecutor = Executors.newFixedThreadPool(
                10, // Max 10 concurrent port scans
                r -> {
                    Thread t = new Thread(r, "port-scanner");
                    t.setDaemon(true);
                    return t;
                }
        );
    }

    /**
     * FIXED: Scan port with backoff for repeatedly failing ports
     */
    public PortScanResult scanPort(int port) {
        // Skip ports that have failed too many times recently
        if (shouldSkipPort(port)) {
            log.trace("Skipping port {} due to repeated failures", port);
            return PortScanResult.noData(port);
        }

        DatagramChannel channel = null;
        Selector selector = null;

        try {
            channel = DatagramChannel.open();
            channel.configureBlocking(false);
            channel.bind(new InetSocketAddress(port));

            selector = Selector.open();
            channel.register(selector, SelectionKey.OP_READ);

            int ready = selector.select(properties.getScannerTimeoutMs());
            if (ready > 0) {
                ByteBuffer buffer = ByteBuffer.allocate(Constants.UDP_BUFFER_SIZE);
                SocketAddress sender = channel.receive(buffer);

                if (sender != null) {
                    log.info("Detected data on port {} from {}", port, sender);
                    // Reset failure count on success
                    failureCount.remove(port);
                    lastScanTime.put(port, System.currentTimeMillis());
                    return PortScanResult.detected(port, sender);
                }
            }

            // Increment failure count
            incrementFailureCount(port);
            return PortScanResult.noData(port);

        } catch (BindException e) {
            log.debug("Port {} already in use", port);
            // Reset failure count - port is in use, not failing
            failureCount.remove(port);
            return PortScanResult.alreadyInUse(port);
        } catch (IOException e) {
            log.warn("Error scanning port {}: {}", port, e.getMessage());
            incrementFailureCount(port);
            return PortScanResult.error(port, e);
        } finally {
            closeQuietly(selector);
            closeQuietly(channel);
        }
    }

    /**
     * FIXED: Parallel scan with limited concurrency
     */
    public List<PortScanResult> scanPorts(List<Integer> ports) {
        // Filter out ports that should be skipped
        List<Integer> portsToScan = ports.stream()
                .filter(port -> !shouldSkipPort(port))
                .toList();

        if (portsToScan.isEmpty()) {
            return new ArrayList<>();
        }

        // Use limited parallelism
        List<Future<PortScanResult>> futures = new ArrayList<>();

        for (Integer port : portsToScan) {
            Future<PortScanResult> future = scanExecutor.submit(() -> scanPort(port));
            futures.add(future);
        }

        // Collect results with timeout
        List<PortScanResult> results = new ArrayList<>();
        for (Future<PortScanResult> future : futures) {
            try {
                PortScanResult result = future.get(properties.getScannerTimeoutMs() + 1000, TimeUnit.MILLISECONDS);
                if (result.hasData()) {
                    results.add(result);
                }
            } catch (TimeoutException e) {
                log.warn("Port scan timed out");
                future.cancel(true);
            } catch (Exception e) {
                log.warn("Error in port scan: {}", e.getMessage());
            }
        }

        return results;
    }

    /**
     * FIXED: Check if port should be skipped due to repeated failures
     */
    private boolean shouldSkipPort(int port) {
        Integer failures = failureCount.get(port);
        if (failures != null && failures >= MAX_FAILURES) {
            Long lastScan = lastScanTime.get(port);
            if (lastScan != null) {
                long timeSinceLastScan = System.currentTimeMillis() - lastScan;
                // Skip if scanned recently and has too many failures
                return timeSinceLastScan < BACKOFF_TIME_MS;
            }
        }
        return false;
    }

    /**
     * Track failure count for backoff
     */
    private void incrementFailureCount(int port) {
        failureCount.merge(port, 1, Integer::sum);
        lastScanTime.put(port, System.currentTimeMillis());
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }
}