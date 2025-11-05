package org.mdt.telemetry_service.exception;

public class PortAlreadyInUseException extends TelemetryException {
    public PortAlreadyInUseException(int port) {
        super("Port " + port + " is already in use");
    }
}
