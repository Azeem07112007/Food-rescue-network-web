package com.foodrescue.util;

import com.foodrescue.exception.ValidationException;
import com.foodrescue.model.enums.UserType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Input Validation and Role Utility Tests")
class ValidationTest {

    @Test
    @DisplayName("Email validator should accept valid email addresses and reject invalid ones")
    void testEmailValidation() {
        assertDoesNotThrow(() -> InputValidator.validateEmail("donor@restaurant.com"));
        assertDoesNotThrow(() -> InputValidator.validateEmail("volunteer.help@ngo.org"));
        assertDoesNotThrow(() -> InputValidator.validateEmail("user+tag@domain.co.in"));

        assertThrows(ValidationException.class, () -> InputValidator.validateEmail("invalid-email"));
        assertThrows(ValidationException.class, () -> InputValidator.validateEmail("@nodomain.com"));
        assertThrows(ValidationException.class, () -> InputValidator.validateEmail("noatsign.com"));
        assertThrows(ValidationException.class, () -> InputValidator.validateEmail(""));
        assertThrows(ValidationException.class, () -> InputValidator.validateEmail(null));
    }

    @Test
    @DisplayName("Email normalization should trim and lowercase emails consistently")
    void testEmailNormalization() {
        assertEquals("test@example.com", InputValidator.normalizeEmail("  Test@EXAMPLE.com  "));
    }

    @Test
    @DisplayName("Non-blank validator should reject empty, whitespace, and null strings")
    void testNonBlankValidation() {
        assertDoesNotThrow(() -> InputValidator.validateNonBlank("Fresh Bread", "Food Name"));
        assertThrows(ValidationException.class, () -> InputValidator.validateNonBlank("", "Food Name"));
        assertThrows(ValidationException.class, () -> InputValidator.validateNonBlank("   ", "Food Name"));
        assertThrows(ValidationException.class, () -> InputValidator.validateNonBlank(null, "Food Name"));
    }

    @Test
    @DisplayName("Positive quantity validator should reject zero and negative integers")
    void testPositiveValidation() {
        assertDoesNotThrow(() -> InputValidator.validatePositive(10, "Quantity"));
        assertThrows(ValidationException.class, () -> InputValidator.validatePositive(0, "Quantity"));
        assertThrows(ValidationException.class, () -> InputValidator.validatePositive(-5, "Quantity"));
    }

    @Test
    @DisplayName("Date order validator should ensure pickup deadline is strictly after available from time")
    void testDateOrderValidation() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime later = now.plusHours(4);

        assertDoesNotThrow(() -> InputValidator.validateDateTimeOrder(now, later, "Start", "Deadline"));
        assertThrows(ValidationException.class, () -> InputValidator.validateDateTimeOrder(later, now, "Start", "Deadline"));
        assertThrows(ValidationException.class, () -> InputValidator.validateDateTimeOrder(now, now, "Start", "Deadline"));
    }

    @Test
    @DisplayName("Future deadline validator should reject deadlines already in the past")
    void testFutureDeadlineValidation() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime past = now.minusMinutes(10);
        LocalDateTime future = now.plusHours(2);

        assertDoesNotThrow(() -> InputValidator.validateFutureDeadline(future, now));
        assertThrows(ValidationException.class, () -> InputValidator.validateFutureDeadline(past, now));
    }

    @Test
    @DisplayName("UserType should correctly categorize provider vs requester roles")
    void testUserTypeRoles() {
        assertTrue(UserType.RESTAURANT.isProvider());
        assertTrue(UserType.HOSTEL.isProvider());
        assertTrue(UserType.CANTEEN.isProvider());
        assertTrue(UserType.EVENT_ORGANIZER.isProvider());
        assertFalse(UserType.RESTAURANT.isRequester());

        assertTrue(UserType.NGO.isRequester());
        assertTrue(UserType.VOLUNTEER.isRequester());
        assertFalse(UserType.NGO.isProvider());
        assertFalse(UserType.VOLUNTEER.isProvider());
    }
}
