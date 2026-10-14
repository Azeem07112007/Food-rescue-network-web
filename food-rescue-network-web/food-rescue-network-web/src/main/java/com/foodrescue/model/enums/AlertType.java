package com.foodrescue.model.enums;

/**
 * Enumeration representing the types of food alerts in the system.
 */
public enum AlertType {
    NEW_DONATION,
    DEADLINE_APPROACHING,
    REQUEST_SUBMITTED,
    REQUEST_APPROVED,
    REQUEST_REJECTED,
    REQUEST_CANCELLED,
    PICKUP_COMPLETED,
    DONATION_EXPIRED;

    public static AlertType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return AlertType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
