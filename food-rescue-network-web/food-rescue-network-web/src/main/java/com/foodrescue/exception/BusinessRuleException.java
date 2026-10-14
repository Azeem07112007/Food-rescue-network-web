package com.foodrescue.exception;

/**
 * Thrown when an operation violates core domain business invariants, role permissions,
 * deadline rules, or quantity overbooking constraints.
 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }

    public BusinessRuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
