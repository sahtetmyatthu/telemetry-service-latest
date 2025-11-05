package org.mdt.telemetry_service.repository;

import org.mdt.telemetry_service.model.TelemetryData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TelemetryRepository extends JpaRepository<TelemetryData, Long> {

    Optional<TelemetryData> findByPort(int port);

    List<TelemetryData> findByGcsIp(String gcsIp);

    @Query("SELECT t FROM TelemetryData t WHERE t.timestamp >= :since")
    List<TelemetryData> findRecentTelemetry(@Param("since") String since);

    void deleteByPort(int port);
}
