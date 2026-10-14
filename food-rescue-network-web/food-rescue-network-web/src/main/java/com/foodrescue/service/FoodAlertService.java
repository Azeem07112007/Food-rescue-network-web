package com.foodrescue.service;

import com.foodrescue.exception.BusinessRuleException;
import com.foodrescue.model.FoodAlert;
import com.foodrescue.model.FoodDonation;
import com.foodrescue.model.User;
import com.foodrescue.model.enums.AlertType;
import com.foodrescue.repository.FoodAlertRepository;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service managing application-level notifications with automated deduplication.
 */
public class FoodAlertService {

    private static final Logger LOGGER = Logger.getLogger(FoodAlertService.class.getName());
    private final FoodAlertRepository alertRepository;

    public FoodAlertService(FoodAlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    public FoodAlertService() {
        this(new FoodAlertRepository());
    }

    /**
     * Creates and persists an alert with deduplication (avoids duplicate alerts within a 2-hour window).
     */
    public boolean sendAlert(int userId, Integer donationId, AlertType alertType, String message) {
        try {
            // Deduplicate within 2 hours
            if (alertRepository.existsRecentAlert(userId, donationId, alertType, 2)) {
                return false;
            }
            FoodAlert alert = new FoodAlert(userId, donationId, alertType, message);
            alertRepository.save(alert);
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to persist alert for user " + userId + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Broadcasts a notification to all registered NGOs/volunteers when a new donation is published.
     */
    public void notifyRequestersOfNewDonation(FoodDonation donation, List<User> requesters) {
        if (requesters == null || requesters.isEmpty()) {
            return;
        }
        String message = String.format("New Surplus Food Published: %s (%d portions) at %s. Pickup deadline: %s",
                donation.getFoodName(), donation.getAvailableQuantity(),
                donation.getPickupLocation(), donation.getPickupDeadline());

        for (User requester : requesters) {
            sendAlert(requester.getUserId(), donation.getDonationId(), AlertType.NEW_DONATION, message);
        }
    }

    public List<FoodAlert> getAlertsForUser(int userId, boolean unreadOnly) {
        try {
            return alertRepository.findByUserId(userId, unreadOnly);
        } catch (SQLException e) {
            throw new BusinessRuleException("Database error loading alerts: " + e.getMessage(), e);
        }
    }

    public boolean markAsRead(int alertId, int userId) {
        try {
            return alertRepository.markAsRead(alertId, userId);
        } catch (SQLException e) {
            throw new BusinessRuleException("Database error updating alert: " + e.getMessage(), e);
        }
    }

    public int markAllAsRead(int userId) {
        try {
            return alertRepository.markAllAsRead(userId);
        } catch (SQLException e) {
            throw new BusinessRuleException("Database error updating alerts: " + e.getMessage(), e);
        }
    }
}
