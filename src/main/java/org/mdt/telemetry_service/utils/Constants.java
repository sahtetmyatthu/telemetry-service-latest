package org.mdt.telemetry_service.utils;

public class Constants {
    private Constants() {
        throw new UnsupportedOperationException("Utility class");
    }

    // Thresholds
    public static final double AIRBORNE_ALTITUDE_THRESHOLD = 0.5;
    public static final int THROTTLE_THRESHOLD = 1050;
    public static final long STALE_DATA_THRESHOLD_MS = 30_000;

    // Buffer Sizes
    public static final int UDP_BUFFER_SIZE = 4096;

    // Earth Radius
    public static final double EARTH_RADIUS_KM = 6371.0;

    // Thread Names
    public static final String THREAD_PORT_SCANNER = "port-scanner";
    public static final String THREAD_LISTENER = "mavlink-listener";
    public static final String THREAD_HEALTH_CHECK = "listener-health-check";

    // WebSocket
    public static final String WS_DRONES_KEY = "drones";
}
