package org.mdt.telemetry_service.utils;

import org.springframework.stereotype.Component;

/**
 * Utility class for calculating distances between geographic coordinates.
 */
@Component
public class DistanceCalculator {

    /**
     * Calculates distance between two coordinates using Haversine formula.
     *
     * @param lat1 Latitude of first point
     * @param lon1 Longitude of first point
     * @param lat2 Latitude of second point
     * @param lon2 Longitude of second point
     * @return distance in kilometers
     */
    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return Constants.EARTH_RADIUS_KM * c;
    }

    /**
     * Converts distance from kilometers to meters.
     */
    public double toMeters(double kilometers) {
        return kilometers * 1000.0;
    }
}
