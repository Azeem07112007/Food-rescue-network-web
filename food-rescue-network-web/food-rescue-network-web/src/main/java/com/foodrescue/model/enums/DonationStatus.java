package com.foodrescue.model.enums;

/**
 * Enumeration representing the lifecycle statuses of a food donation.
 */
public enum DonationStatus {
    AVAILABLE,
    RESERVED,
    COLLECTED,
    EXPIRED,
    CANCELLED;

    public static DonationStatus fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return DonationStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
