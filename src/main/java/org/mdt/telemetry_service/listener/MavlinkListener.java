package org.mdt.telemetry_service.listener;

import io.dronefleet.mavlink.MavlinkConnection;
import io.dronefleet.mavlink.MavlinkMessage;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.mdt.telemetry_service.config.TelemetryProperties;
import org.mdt.telemetry_service.processor.MavlinkMessageProcessor;
import org.mdt.telemetry_service.utils.Constants;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;


/**
 * Simplified MAVLink listener - only responsible for listening,
 * not for managing its own lifecycle.
 */
@Component
@Slf4j
public class MavlinkListener {

    private final ExecutorService executorService;
    private final TelemetryProperties properties;
    private final MavlinkMessageProcessor messageProcessor;

    public MavlinkListener(
            TelemetryProperties properties,
            MavlinkMessageProcessor messageProcessor) {
        this.properties = properties;
        this.messageProcessor = messageProcessor;
        this.executorService = Executors.newCachedThreadPool(
                r -> new Thread(r, Constants.THREAD_LISTENER));
    }

    /**
     * Starts listening on a port. Returns a Future for lifecycle management.
     *
     * @param port Port to listen on
     * @return Future representing the listening task
     */
    public Future<?> listenOnPort(int port) {
        return executorService.submit(() -> listen(port));
    }

    private void listen(int port) {
        log.info("Starting MAVLink listener on port {}", port);

        try (DatagramSocket socket = createSocket(port);
             UdpInputStream inputStream = new UdpInputStream(socket)) {

            MavlinkConnection connection = MavlinkConnection.create(inputStream, null);

            long lastMessageTime = System.currentTimeMillis();

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    MavlinkMessage<?> message = connection.next();

                    if (message != null) {
                        InetAddress sender = inputStream.getSenderAddress();
                        messageProcessor.processMessage(message, port, sender);
                        lastMessageTime = System.currentTimeMillis();
                    }

                    // Check for timeout
                    if (isIdle(lastMessageTime)) {
                        log.info("Port {} idle timeout, stopping listener", port);
                        break;
                    }

                } catch (IOException e) {
                    if (isIdle(lastMessageTime)) {
                        log.info("Port {} timeout with no data", port);
                        break;
                    }
                    // Otherwise continue
                }
            }

        } catch (IOException e) {
            if (!Thread.currentThread().isInterrupted()) {
                log.error("Error in listener on port {}", port, e);
            }
        } finally {
            log.info("MAVLink listener stopped on port {}", port);
        }
    }

    private DatagramSocket createSocket(int port) throws IOException {
        DatagramSocket socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress("0.0.0.0", port));
        return socket;
    }

    private boolean isIdle(long lastMessageTime) {
        return (System.currentTimeMillis() - lastMessageTime)
                > properties.getIdleThresholdMs();
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down MAVLink listener");
        executorService.shutdown();

        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
