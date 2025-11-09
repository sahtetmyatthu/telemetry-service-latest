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
import java.util.concurrent.*;

/**
 * Fixed MAVLink listener with bounded thread pool
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

        // FIXED: Use bounded thread pool instead of CachedThreadPool
        int maxThreads = properties.getMaxPorts(); // Use max-ports as thread limit
        this.executorService = new ThreadPoolExecutor(
                50,                          // core pool size
                maxThreads,                  // maximum pool size
                60L, TimeUnit.SECONDS,       // keep alive time
                new LinkedBlockingQueue<>(100), // bounded queue
                new ThreadFactory() {
                    private int counter = 0;
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, Constants.THREAD_LISTENER + "-" + counter++);
                        t.setDaemon(true); // Make threads daemon to prevent JVM hanging
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy() // Back-pressure: run in caller thread if pool full
        );

        log.info("Initialized MavlinkListener with max {} threads", maxThreads);
    }

    /**
     * Starts listening on a port. Returns a Future for lifecycle management.
     */
    public Future<?> listenOnPort(int port) {
        try {
            return executorService.submit(() -> listen(port));
        } catch (RejectedExecutionException e) {
            log.error("Thread pool full, cannot start listener on port {}", port);
            throw new RuntimeException("Thread pool exhausted", e);
        }
    }

    private void listen(int port) {
        log.info("Starting MAVLink listener on port {}", port);

        DatagramSocket socket = null;
        UdpInputStream inputStream = null;
        MavlinkConnection connection = null;

        try {
            socket = createSocket(port);
            inputStream = new UdpInputStream(socket);
            connection = MavlinkConnection.create(inputStream, null);

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
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    if (isIdle(lastMessageTime)) {
                        log.info("Port {} timeout with no data", port);
                        break;
                    }
                    // Brief pause on error to prevent CPU spinning
                    Thread.sleep(10);
                }
            }

        } catch (Exception e) {
            if (!Thread.currentThread().isInterrupted()) {
                log.error("Error in listener on port {}", port, e);
            }
        } finally {
            // FIXED: Proper resource cleanup
            closeQuietly(inputStream);
            closeQuietly(socket);
            log.info("MAVLink listener stopped on port {}", port);
        }
    }

    private DatagramSocket createSocket(int port) throws IOException {
        DatagramSocket socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        socket.setSoTimeout(1000); // FIXED: Add socket timeout to prevent blocking forever
        socket.bind(new InetSocketAddress("0.0.0.0", port));
        return socket;
    }

    private boolean isIdle(long lastMessageTime) {
        return (System.currentTimeMillis() - lastMessageTime) > properties.getIdleThresholdMs();
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.debug("Error closing resource", e);
            }
        }
    }

    private void closeQuietly(DatagramSocket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (Exception e) {
                log.debug("Error closing socket", e);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down MAVLink listener");
        executorService.shutdown();

        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("Forcing shutdown of listener threads");
                executorService.shutdownNow();

                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.error("Listener thread pool did not terminate");
                }
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}