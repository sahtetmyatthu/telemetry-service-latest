package org.mdt.telemetry_service.processor;

import io.dronefleet.mavlink.MavlinkMessage;
import io.dronefleet.mavlink.ardupilotmega.Wind;
import io.dronefleet.mavlink.common.*;
import lombok.extern.slf4j.Slf4j;
import org.mdt.telemetry_service.model.TelemetryData;
import org.mdt.telemetry_service.service.TelemetryService;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

/**
        * Processes incoming MAVLink messages and updates telemetry data.
 */
@Component
@Slf4j
public class MavlinkMessageProcessor {

    private final TelemetryUpdateService updateService;
    private final TelemetryService telemetryService;

    public MavlinkMessageProcessor(
            TelemetryUpdateService updateService,
            TelemetryService telemetryService) {
        this.updateService = updateService;
        this.telemetryService = telemetryService;
    }

    /**
     * Processes a MAVLink message and updates telemetry data.
     */
    public void processMessage(MavlinkMessage<?> message, int port, InetAddress senderAddress) {
        try {
            TelemetryData telemetryData = telemetryService.getOrCreateTelemetryData(port);
            telemetryData.setGcsIp(senderAddress.getHostAddress());
            telemetryData.setSystemId(message.getOriginSystemId());

            // Update last activity time
            telemetryService.updateActivity(port);

            Object payload = message.getPayload();

            if (payload instanceof GlobalPositionInt pos) {
                updateService.applyGlobalPosition(telemetryData, pos, port);
            } else if (payload instanceof SysStatus sysStatus) {
                updateService.applySysStatus(telemetryData, sysStatus);
            } else if (payload instanceof VfrHud vfrHud) {
                updateService.applyVfrHud(telemetryData, vfrHud, port);
            } else if (payload instanceof MissionCount missionCount) {
                updateService.onMissionCount(port, missionCount);
            } else if (payload instanceof MissionItemInt missionItemInt) {
                updateService.onMissionItemInt(port, missionItemInt);
            } else if (payload instanceof Wind wind) {
                updateService.applyWind(telemetryData, wind);
            } else if (payload instanceof GpsRawInt gpsRawInt) {
                updateService.applyGpsRaw(telemetryData, gpsRawInt);
            } else if (payload instanceof Attitude attitude) {
                updateService.applyAttitude(telemetryData, attitude);
            } else if (payload instanceof NavControllerOutput navControllerOutput) {
                updateService.applyWpDist(telemetryData, navControllerOutput);
            } else if (payload instanceof ServoOutputRaw servo) {
                updateService.applyServoOutputs(telemetryData, servo);
            }

            // Save updated telemetry
            telemetryService.updateTelemetry(telemetryData);

        } catch (Exception e) {
            log.error("Error processing MAVLink message on port {}: {}", port, e.getMessage(), e);
        }
    }
}
