package org.mdt.telemetry_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaypointDto {

    private Integer seq;
    private Double lat;
    private Double lon;
    private Double alt;
}
