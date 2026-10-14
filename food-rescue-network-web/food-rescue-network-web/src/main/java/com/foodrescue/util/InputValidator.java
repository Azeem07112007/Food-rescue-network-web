package com.foodrescue.util;

import com.foodrescue.exception.ValidationException;
import com.foodrescue.model.enums.UserType;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Utility class providing validation functions for inputs across all layers.
 */
public final class InputValidator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private InputValidator() {
        // Utility class
    }

    public static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    public static void validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new ValidationException("Email cannot be empty.");
        }
        String normalized = email.trim();
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new ValidationException("Invalid email format: '" + email + "'. Expected format: user@example.com");
        }
    }

    public static void validateNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException(fieldName + " cannot be blank.");
        }
    }

    public static void validatePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new ValidationException(fieldName + " must be a positive integer greater than 0. Provided: " + value);
        }
    }

    public static void validatePositiveOrZero(int value, String fieldName) {
        if (value < 0) {
            throw new ValidationException(fieldName + " cannot be negative. Provided: " + value);
        }
    }

    public static void validateDateTimeOrder(LocalDateTime start, LocalDateTime end, String startLabel, String endLabel) {
        if (start == null || end == null) {
            throw new ValidationException("Both " + startLabel + " and " + endLabel + " must be provided.");
        }
        if (!end.isAfter(start)) {
            throw new ValidationException(endLabel + " (" + end + ") must be strictly after " + startLabel + " (" + start + ").");
        }
    }

    public static void validateFutureDeadline(LocalDateTime deadline, LocalDateTime now) {
        if (deadline == null) {
            throw new ValidationException("Pickup deadline must be specified.");
        }
        if (!deadline.isAfter(now)) {
            throw new ValidationException("Pickup deadline (" + deadline + ") has already expired relative to current time (" + now + ").");
        }
    }

    public static UserType validateUserType(String typeStr) {
        if (typeStr == null || typeStr.trim().isEmpty()) {
            throw new ValidationException("User type cannot be empty.");
        }
        UserType type = UserType.fromString(typeStr);
        if (type == null) {
            throw new ValidationException("Invalid user type: '" + typeStr + "'. Allowed types: RESTAURANT, HOSTEL, CANTEEN, EVENT_ORGANIZER, NGO, VOLUNTEER");
        }
        return type;
    }
}
