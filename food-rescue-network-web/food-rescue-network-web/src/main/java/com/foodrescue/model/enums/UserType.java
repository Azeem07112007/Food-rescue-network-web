package com.foodrescue.model.enums;

/**
 * Enumeration representing the valid user types in the Food Rescue Network.
 * Providers publish donations; Requesters request surplus food.
 */
public enum UserType {
    RESTAURANT,
    HOSTEL,
    CANTEEN,
    EVENT_ORGANIZER,
    NGO,
    VOLUNTEER;

    /**
     * Determines if this user role is authorized to publish and manage food donations.
     */
    public boolean isProvider() {
        return this == RESTAURANT || this == HOSTEL || this == CANTEEN || this == EVENT_ORGANIZER;
    }

    /**
     * Determines if this user role is authorized to search and request food pickups.
     */
    public boolean isRequester() {
        return this == NGO || this == VOLUNTEER;
    }

    /**
     * Parses a string safely into a UserType.
     */
    public static UserType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return UserType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
