package com.foodrescue.model;

import com.foodrescue.model.enums.AlertType;
import java.time.LocalDateTime;

/**
 * Model entity representing an application-level alert notification.
 */
public class FoodAlert {
    private int alertId;
    private int userId;
    private Integer donationId; // nullable
    private AlertType alertType;
    private String message;
    private boolean isRead;
    private LocalDateTime createdAt;

    public FoodAlert() {
    }

    public FoodAlert(int alertId, int userId, Integer donationId, AlertType alertType, String message, boolean isRead, LocalDateTime createdAt) {
        this.alertId = alertId;
        this.userId = userId;
        this.donationId = donationId;
        this.alertType = alertType;
        this.message = message;
        this.isRead = isRead;
        this.createdAt = createdAt;
    }

    public FoodAlert(int userId, Integer donationId, AlertType alertType, String message) {
        this.userId = userId;
        this.donationId = donationId;
        this.alertType = alertType;
        this.message = message;
        this.isRead = false;
        this.createdAt = LocalDateTime.now();
    }

    public int getAlertId() {
        return alertId;
    }

    public void setAlertId(int alertId) {
        this.alertId = alertId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public Integer getDonationId() {
        return donationId;
    }

    public void setDonationId(Integer donationId) {
        this.donationId = donationId;
    }

    public AlertType getAlertType() {
        return alertType;
    }

    public void setAlertType(AlertType alertType) {
        this.alertType = alertType;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return String.format("[%s] Alert #%d (%s) - %s | %s",
                isRead ? "READ" : "UNREAD", alertId, alertType, message, createdAt);
    }
}
