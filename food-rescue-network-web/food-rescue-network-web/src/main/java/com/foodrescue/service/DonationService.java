package com.foodrescue.service;

import com.foodrescue.exception.BusinessRuleException;
import com.foodrescue.exception.NotFoundException;
import com.foodrescue.model.FoodDonation;
import com.foodrescue.model.User;
import com.foodrescue.model.enums.DonationStatus;
import com.foodrescue.repository.DonationRepository;
import com.foodrescue.repository.UserRepository;
import com.foodrescue.util.InputValidator;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service managing food donation publication, searching, and status queries.
 */
public class DonationService {

    private final DonationRepository donationRepository;
    private final UserRepository userRepository;
    private final FoodAlertService alertService;
    private final Clock clock;

    public DonationService(DonationRepository donationRepository, UserRepository userRepository,
                           FoodAlertService alertService, Clock clock) {
        this.donationRepository = donationRepository;
        this.userRepository = userRepository;
        this.alertService = alertService;
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
    }

    public DonationService(DonationRepository donationRepository, UserRepository userRepository, FoodAlertService alertService) {
        this(donationRepository, userRepository, alertService, Clock.systemDefaultZone());
    }

    public DonationService() {
        this(new DonationRepository(), new UserRepository(), new FoodAlertService(), Clock.systemDefaultZone());
    }

    /**
     * Feature 2 — Publish Food Donations
     * Validates provider role, quantities, and availability windows before inserting.
     */
    public FoodDonation publishDonation(int providerId, String foodName, String foodCategory,
                                       int quantity, String pickupLocation,
                                       LocalDateTime availableFrom, LocalDateTime pickupDeadline) {
        // 1. Role validation
        User provider;
        try {
            provider = userRepository.findById(providerId)
                    .orElseThrow(() -> new NotFoundException("Provider user not found with ID: " + providerId));
        } catch (SQLException e) {
            throw new BusinessRuleException("Database error verifying provider: " + e.getMessage(), e);
        }

        if (!provider.getUserType().isProvider()) {
            throw new BusinessRuleException("Role violation: User #" + providerId + " has role '" +
                    provider.getUserType() + "' and is not authorized to publish food donations. Only providers can publish.");
        }

        // 2. Field validations
        InputValidator.validateNonBlank(foodName, "Food name");
        InputValidator.validateNonBlank(foodCategory, "Food category");
        InputValidator.validatePositive(quantity, "Quantity");
        InputValidator.validateNonBlank(pickupLocation, "Pickup location");
        InputValidator.validateDateTimeOrder(availableFrom, pickupDeadline, "Available from", "Pickup deadline");

        LocalDateTime now = LocalDateTime.now(clock);
        InputValidator.validateFutureDeadline(pickupDeadline, now);

        // 3. Construct donation with consistent quantity invariants
        FoodDonation donation = new FoodDonation(
                providerId,
                foodName.trim(),
                foodCategory.trim(),
                quantity,
                pickupLocation.trim(),
                availableFrom,
                pickupDeadline
        );
        donation.setStatus(DonationStatus.AVAILABLE);

        try {
            FoodDonation saved = donationRepository.save(donation);

            // 4. Alert registered NGOs and volunteers about the newly available donation
            try {
                List<User> requesters = userRepository.findRequesters();
                alertService.notifyRequestersOfNewDonation(saved, requesters);
            } catch (Exception e) {
                // Non-critical alert error should not abort the publication
            }

            return saved;
        } catch (SQLException e) {
            throw new BusinessRuleException("Database error saving food donation: " + e.getMessage(), e);
        }
    }

    /**
     * Feature 3 — Search Available Food
     * Parameterized search for currently available, non-expired donations.
     */
    public List<FoodDonation> searchAvailable(String category, Integer minQuantity, String location) {
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            return donationRepository.searchAvailable(category, minQuantity, location, now);
        } catch (SQLException e) {
            throw new BusinessRuleException("Database error searching available food: " + e.getMessage(), e);
        }
    }

    public FoodDonation getDonationById(int donationId) {
        try {
            return donationRepository.findById(donationId)
                    .orElseThrow(() -> new NotFoundException("Food donation not found with ID: " + donationId));
        } catch (SQLException e) {
            throw new BusinessRuleException("Database error retrieving donation: " + e.getMessage(), e);
        }
    }

    public List<FoodDonation> getDonationsByProvider(int providerId) {
        try {
            return donationRepository.findByProviderId(providerId);
        } catch (SQLException e) {
            throw new BusinessRuleException("Database error retrieving provider donations: " + e.getMessage(), e);
        }
    }
}
