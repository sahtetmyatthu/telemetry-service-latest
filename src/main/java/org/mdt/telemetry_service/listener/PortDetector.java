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
import java.util.List;
import java.util.stream.Collectors;

/**
 * Responsible ONLY for detecting which ports have incoming data.
 * Does NOT manage listeners or long-lived connections.
 */

@Component
@Slf4j
public class PortDetector {

    private final TelemetryProperties properties;

    public PortDetector(TelemetryProperties properties) {
        this.properties = properties;
    }


    /**
     * Scans a single port for incoming data.
     *
     * @param port Port number to scan
     * @return PortScanResult with detection status
     */
    public PortScanResult scanPort(int port) {
        try(DatagramChannel channel = DatagramChannel.open()){
            channel.configureBlocking(false);
            channel.bind(new InetSocketAddress(port));

            try (Selector selector = Selector.open()){
                channel.register(selector, SelectionKey.OP_READ);

                int ready = selector.select(properties.getScannerTimeoutMs());
                if (ready > 0) {
                    ByteBuffer buffer = ByteBuffer.allocate(Constants.UDP_BUFFER_SIZE);
                    SocketAddress sender = channel.receive(buffer);

                    if (sender != null) {
                        log.info("Detected data on port {} from {}", port, sender);
                        return PortScanResult.detected(port, sender);
                    }
                }

                return PortScanResult.noData(port);

            }
        }catch (BindException e) {
            log.debug("Port {} already in use", port);
            return PortScanResult.alreadyInUse(port);
        } catch (IOException e) {
            log.warn("Error scanning port {}: {}", port, e.getMessage());
            return PortScanResult.error(port, e);
        }



    }

    /**
     * Scans multiple ports concurrently.
     *
     * @param ports List of ports to scan
     * @return List of results for ports that have data
     */

    public List<PortScanResult> scanPorts(List<Integer> ports) {
        return ports.parallelStream()
                .map(this::scanPort)
                .filter(PortScanResult::hasData)
                .collect(Collectors.toList());
    }
}
