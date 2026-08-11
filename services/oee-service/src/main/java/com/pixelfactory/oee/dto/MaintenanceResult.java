package com.pixelfactory.oee.dto;

public record MaintenanceResult(int rolledUpHours, long purgedCycleEvents, int purgedStatusEvents) {
}
