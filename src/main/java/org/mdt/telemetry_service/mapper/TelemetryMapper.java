package org.mdt.telemetry_service.mapper;

import org.mdt.telemetry_service.dto.HomeLocationDto;
import org.mdt.telemetry_service.dto.TelemetryDataDto;
import org.mdt.telemetry_service.dto.WaypointDto;
import org.mdt.telemetry_service.model.TelemetryData;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps between TelemetryData entity and DTOs.
 */
@Component
public class TelemetryMapper {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Converts TelemetryData entity to DTO.
     */
    public TelemetryDataDto toDto(TelemetryData data) {
        return TelemetryDataDto.builder()
                .port(data.getPort())
                .gcsIp(data.getGcsIp())
                .systemId(data.getSystemId())
                .lat(data.getLat())
                .lon(data.getLon())
                .alt(data.getAlt())
                .distTraveled(data.getDistTraveled())
                .wpDist(data.getWpDist())
                .heading(data.getHeading())
                .targetHeading(data.getTargetHeading())
                .distToHome(data.getDistToHome())
                .verticalSpeed(data.getVerticalSpeed())
                .groundSpeed(data.getGroundSpeed())
                .windVel(data.getWindVel())
                .airspeed(data.getAirspeed())
                .gpsHdop(data.getGpsHdop())
                .roll(data.getRoll())
                .pitch(data.getPitch())
                .yaw(data.getYaw())
                .ch3percent(data.getCh3percent())
                .ch3out(data.getCh3out())
                .tot(data.getTot())
                .toh(data.getToh())
                .timeInAir(data.getTimeInAir())
                .batteryVoltage(data.getBatteryVoltage())
                .batteryCurrent(data.getBatteryCurrent())
                .waypointsCount(data.getWaypointsCount())
                .flightStatus(data.getFlightStatus())
                .throttleActive(data.isThrottleActive())
                .flying(data.isFlying())
                .homeLocation(toHomeLocationDto(data.getHomeLocation()))
                .waypoints(toWaypointDtos(data.getWaypoints()))
                .timestamp(parseTimestamp(data.getTimestamp()))
                .build();
    }

    /**
     * Converts TelemetryData entity to DTO with additional waypoints and home location.
     */
    public TelemetryDataDto toDto(
            TelemetryData data,
            List<TelemetryData.Waypoint> waypoints,
            TelemetryData.HomeLocation homeLocation) {

        TelemetryDataDto dto = toDto(data);

        if (waypoints != null && !waypoints.isEmpty()) {
            dto.setWaypoints(toWaypointDtos(waypoints));
            dto.setWaypointsCount(waypoints.size());
        }

        if (homeLocation != null) {
            dto.setHomeLocation(toHomeLocationDto(homeLocation));
        }

        return dto;
    }

    private HomeLocationDto toHomeLocationDto(TelemetryData.HomeLocation homeLocation) {
        if (homeLocation == null) {
            return HomeLocationDto.builder().lat(0.0).lon(0.0).build();
        }

        return HomeLocationDto.builder()
                .lat(homeLocation.getLat())
                .lon(homeLocation.getLon())
                .build();
    }

    private List<WaypointDto> toWaypointDtos(List<TelemetryData.Waypoint> waypoints) {
        if (waypoints == null) {
            return Collections.emptyList();
        }

        return waypoints.stream()
                .map(wp -> WaypointDto.builder()
                        .seq(wp.getSeq())
                        .lat(wp.getLat())
                        .lon(wp.getLon())
                        .alt(wp.getAlt())
                        .build())
                .collect(Collectors.toList());
    }

    private LocalDateTime parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return LocalDateTime.now();
        }

        try {
            return LocalDateTime.parse(timestamp, FORMATTER);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}
