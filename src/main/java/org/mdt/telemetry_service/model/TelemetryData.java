package org.mdt.telemetry_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "telemetry_data")
@Getter
@Setter
public class TelemetryData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int port;
    private String gcsIp;
    private int systemId;
    private double lat;
    private double lon;
    private double alt;
    private double distTraveled;
    private double wpDist;
    private float heading;
    private float targetHeading;
    private float previousHeading;
    private double distToHome;
    private double verticalSpeed;
    private double groundSpeed;
    private double windVel;
    private double airspeed;
    private double gpsHdop;
    private double roll;
    private double pitch;
    private double yaw;
    private double ch3percent;
    private int ch3out;
    private int ch9out;
    private int ch10out;
    private int ch11out;
    private int ch12out;
    private double tot;
    private double toh;
    private long timeInAir;
    private double batteryVoltage;
    private double batteryCurrent;
    private int waypointsCount;

    private boolean airborne;
    private long startTime;
    private long throttleStartTime;
    private long totalThrottleTime;
    private boolean throttleActive;
    private boolean flying;
    private long flightStartTime;
    private int autoTime;
    private int flightStatus;

    private String arrivalTime;
    private String timestamp;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "lat", column = @Column(name = "home_lat")),
            @AttributeOverride(name = "lon", column = @Column(name = "home_lon"))
    })
    private HomeLocation homeLocation = new HomeLocation();

    @ElementCollection
    @CollectionTable(name = "telemetry_waypoints", joinColumns = @JoinColumn(name = "telemetry_id"))
    @AttributeOverrides({
            @AttributeOverride(name = "seq", column = @Column(name = "wp_seq")),
            @AttributeOverride(name = "lat", column = @Column(name = "wp_lat")),
            @AttributeOverride(name = "lon", column = @Column(name = "wp_lon")),
            @AttributeOverride(name = "alt", column = @Column(name = "wp_alt"))
    })
    private List<Waypoint> waypoints = new ArrayList<>();

    public TelemetryData() {
        initializeDefaults();
    }

    public TelemetryData(int port) {
        this();
        this.port = port;
    }

    private void initializeDefaults() {
        this.port = 0;
        this.gcsIp = "Unknown";
        this.systemId = 0;
        this.lat = 0.0;
        this.lon = 0.0;
        this.alt = 0.0;
        this.distTraveled = 0.0;
        this.wpDist = 0.0;
        this.heading = 0;
        this.targetHeading = 0;
        this.previousHeading = 0;
        this.distToHome = 0.0;
        this.verticalSpeed = 0.0;
        this.groundSpeed = 0.0;
        this.windVel = 0.0;
        this.airspeed = 0.0;
        this.gpsHdop = 0.0;
        this.roll = 0.0;
        this.pitch = 0.0;
        this.yaw = 0.0;
        this.ch3percent = 0.0;
        this.ch3out = 0;
        this.ch9out = 0;
        this.ch10out = 0;
        this.ch11out = 0;
        this.ch12out = 0;
        this.tot = 0.0;
        this.toh = 0.0;
        this.timeInAir = 0;
        this.batteryVoltage = 0.0;
        this.batteryCurrent = 0.0;
        this.waypointsCount = 0;
        this.airborne = false;
        this.startTime = 0L;
        this.throttleStartTime = 0L;
        this.totalThrottleTime = 0L;
        this.throttleActive = false;
        this.flying = false;
        this.flightStartTime = 0L;
        this.autoTime = 0;
        this.flightStatus = 0;
        this.arrivalTime = "";
        this.timestamp = "";
    }

    @Embeddable
    @Getter
    @Setter
    public static class HomeLocation {
        private double lat;
        private double lon;

        public HomeLocation() {
            this.lat = 0.0;
            this.lon = 0.0;
        }
    }

    @Embeddable
    @Getter
    @Setter
    public static class Waypoint {
        private int seq;
        private double lat;
        private double lon;
        private double alt;

        public Waypoint() {
            this.seq = 0;
            this.lat = 0.0;
            this.lon = 0.0;
            this.alt = 0.0;
        }
    }
}
