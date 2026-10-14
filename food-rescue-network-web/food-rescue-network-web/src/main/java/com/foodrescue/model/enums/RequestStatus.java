package com.foodrescue.model.enums;

/**
 * Enumeration representing the statuses of a pickup request.
 */
public enum RequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    COMPLETED,
    CANCELLED;

    public static RequestStatus fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return RequestStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
