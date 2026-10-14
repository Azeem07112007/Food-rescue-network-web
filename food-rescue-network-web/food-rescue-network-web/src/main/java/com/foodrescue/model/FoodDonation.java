package com.foodrescue.model;

import com.foodrescue.model.enums.DonationStatus;
import java.time.LocalDateTime;

/**
 * Model entity representing a food donation.
 * Mapped to the existing 'food_donations' table with 'quantity' and 'remaining_quantity'.
 */
public class FoodDonation {
    private int donationId;
    private int providerId;
    private String foodName;
    private String foodCategory;
    private int quantity; // Initial published total quantity
    private int remainingQuantity; // Current available quantity in database
    private int reservedQuantity; // Approved portions awaiting collection (derived from pickup_requests)
    private String pickupLocation;
    private LocalDateTime availableFrom;
    private LocalDateTime pickupDeadline;
    private DonationStatus status;

    // Optional provider details for display in console search/matching
    private String providerName;
    private String providerLocation;

    public FoodDonation() {
    }

    public FoodDonation(int donationId, int providerId, String foodName, String foodCategory,
                        int quantity, int remainingQuantity,
                        String pickupLocation, LocalDateTime availableFrom, LocalDateTime pickupDeadline,
                        DonationStatus status) {
        this.donationId = donationId;
        this.providerId = providerId;
        this.foodName = foodName;
        this.foodCategory = foodCategory;
        this.quantity = quantity;
        this.remainingQuantity = remainingQuantity;
        this.pickupLocation = pickupLocation;
        this.availableFrom = availableFrom;
        this.pickupDeadline = pickupDeadline;
        this.status = status;
    }

    public FoodDonation(int providerId, String foodName, String foodCategory, int quantity,
                        String pickupLocation, LocalDateTime availableFrom, LocalDateTime pickupDeadline) {
        this.providerId = providerId;
        this.foodName = foodName;
        this.foodCategory = foodCategory;
        this.quantity = quantity;
        this.remainingQuantity = quantity;
        this.reservedQuantity = 0;
        this.pickupLocation = pickupLocation;
        this.availableFrom = availableFrom;
        this.pickupDeadline = pickupDeadline;
        this.status = DonationStatus.AVAILABLE;
    }

    public int getDonationId() {
        return donationId;
    }

    public void setDonationId(int donationId) {
        this.donationId = donationId;
    }

    public int getProviderId() {
        return providerId;
    }

    public void setProviderId(int providerId) {
        this.providerId = providerId;
    }

    public String getFoodName() {
        return foodName;
    }

    public void setFoodName(String foodName) {
        this.foodName = foodName;
    }

    public String getFoodCategory() {
        return foodCategory;
    }

    public void setFoodCategory(String foodCategory) {
        this.foodCategory = foodCategory;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getRemainingQuantity() {
        return remainingQuantity;
    }

    public void setRemainingQuantity(int remainingQuantity) {
        this.remainingQuantity = remainingQuantity;
    }

    // Backward-compatible alias helpers
    public int getTotalQuantity() {
        return quantity;
    }

    public void setTotalQuantity(int totalQuantity) {
        this.quantity = totalQuantity;
    }

    public int getAvailableQuantity() {
        return remainingQuantity;
    }

    public void setAvailableQuantity(int availableQuantity) {
        this.remainingQuantity = availableQuantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public void setReservedQuantity(int reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }

    public String getPickupLocation() {
        return pickupLocation;
    }

    public void setPickupLocation(String pickupLocation) {
        this.pickupLocation = pickupLocation;
    }

    public LocalDateTime getAvailableFrom() {
        return availableFrom;
    }

    public void setAvailableFrom(LocalDateTime availableFrom) {
        this.availableFrom = availableFrom;
    }

    public LocalDateTime getPickupDeadline() {
        return pickupDeadline;
    }

    public void setPickupDeadline(LocalDateTime pickupDeadline) {
        this.pickupDeadline = pickupDeadline;
    }

    public DonationStatus getStatus() {
        return status;
    }

    public void setStatus(DonationStatus status) {
        this.status = status;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getProviderLocation() {
        return providerLocation;
    }

    public void setProviderLocation(String providerLocation) {
        this.providerLocation = providerLocation;
    }

    @Override
    public String toString() {
        return String.format(
            "Donation #%d: '%s' [%s] | Total: %d, Remaining: %d | Location: '%s' | Available From: %s | Deadline: %s | Status: %s",
            donationId, foodName, foodCategory, quantity, remainingQuantity,
            pickupLocation, availableFrom, pickupDeadline, status
        );
    }
}
