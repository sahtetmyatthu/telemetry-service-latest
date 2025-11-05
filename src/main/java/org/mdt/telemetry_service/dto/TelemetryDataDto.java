package org.mdt.telemetry_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryDataDto {

    private Integer port;
    private String gcsIp;
    private Integer systemId;
    private Double lat;
    private Double lon;
    private Double alt;
    private Double distTraveled;
    private Double wpDist;
    private Float heading;
    private Float targetHeading;
    private Double distToHome;
    private Double verticalSpeed;
    private Double groundSpeed;
    private Double windVel;
    private Double airspeed;
    private Double gpsHdop;
    private Double roll;
    private Double pitch;
    private Double yaw;
    private Double ch3percent;
    private Integer ch3out;
    private Double tot;
    private Double toh;
    private Long timeInAir;
    private Double batteryVoltage;
    private Double batteryCurrent;
    private Integer waypointsCount;
    private Integer flightStatus;
    private Boolean throttleActive;
    private Boolean flying;
    private HomeLocationDto homeLocation;
    private List<WaypointDto> waypoints;
    private LocalDateTime timestamp;
}
