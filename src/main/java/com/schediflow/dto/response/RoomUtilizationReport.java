package com.schediflow.dto.response;

import java.util.List;
import java.util.Map;

/** EXPORT-06: how fully each room is used across the cycle. */
public record RoomUtilizationReport(
        List<RoomUtilizationRow> rooms,
        Map<String, Double> avgOccupancyByType,
        int totalPeriodsInCycle
) {

    /** @param occupancyByPeriod period name → percentage of days that period is occupied */
    public record RoomUtilizationRow(
            Long roomId,
            String roomName,
            String roomType,
            Map<String, Double> occupancyByPeriod,
            double avgOccupancy) {}
}
