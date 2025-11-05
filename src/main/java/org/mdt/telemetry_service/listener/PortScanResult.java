package org.mdt.telemetry_service.listener;

import lombok.Builder;
import lombok.Value;

import java.net.SocketAddress;

/**
 * Immutable result of a port scan operation.
 */
@Value
@Builder
public class PortScanResult {

    int port;
    ScanStatus status;
    SocketAddress sender;
    Exception error;

    public enum ScanStatus {
        DATA_DETECTED,
        NO_DATA,
        ALREADY_IN_USE,
        ERROR
    }

    public static PortScanResult detected(int port, SocketAddress sender) {
        return PortScanResult.builder()
                .port(port)
                .status(ScanStatus.DATA_DETECTED)
                .sender(sender)
                .build();
    }

    public static PortScanResult noData(int port) {
        return PortScanResult.builder()
                .port(port)
                .status(ScanStatus.NO_DATA)
                .build();
    }

    public static PortScanResult alreadyInUse(int port) {
        return PortScanResult.builder()
                .port(port)
                .status(ScanStatus.ALREADY_IN_USE)
                .build();
    }

    public static PortScanResult error(int port, Exception error) {
        return PortScanResult.builder()
                .port(port)
                .status(ScanStatus.ERROR)
                .error(error)
                .build();
    }

    public boolean hasData() {
        return status == ScanStatus.DATA_DETECTED;
    }
}
