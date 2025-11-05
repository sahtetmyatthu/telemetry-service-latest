package org.mdt.telemetry_service.config;


import io.dronefleet.mavlink.common.MavCmd;
import io.dronefleet.mavlink.common.MissionClearAll;
import io.dronefleet.mavlink.common.SetPositionTargetGlobalInt;
import io.dronefleet.mavlink.minimal.MavComponent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "telemetry")
@Validated
@Data
public class TelemetryProperties {

    @Min(1)
    private int threadPoolSize;

    @Min(1000)
    private int idleThresholdMs;

    @Min(1000)
    private int scannerTimeoutMs;

    @Min(1)
    private int maxPorts;

    @Min(265)
    private int bufferSize;

    @Valid
    private PortRange portRange = new PortRange();

    @Data
    public static class PortRange {
        @Min(1)
        private int min;

        @Max(65535)
        private int max;
    }




}
