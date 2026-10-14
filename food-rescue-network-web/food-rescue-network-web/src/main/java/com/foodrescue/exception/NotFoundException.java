package com.foodrescue.exception;

/**
 * Thrown when a requested entity (User, Donation, Request, Alert) cannot be found in the database.
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
