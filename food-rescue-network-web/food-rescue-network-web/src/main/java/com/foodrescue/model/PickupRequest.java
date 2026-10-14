package com.foodrescue.model;

import com.foodrescue.model.enums.RequestStatus;
import java.time.LocalDateTime;

/**
 * Model entity representing a pickup request made by an NGO or Volunteer.
 * Mapped to the existing 'pickup_requests' table.
 */
public class PickupRequest {
    private int requestId;
    private int donationId;
    private int requesterId;
    private int requestedQuantity;
    private LocalDateTime requestTime;
    private RequestStatus status;
    private String remarks; // in-memory note if entered

    // Display helpers
    private String foodName;
    private String requesterName;
    private int providerId;

    public PickupRequest() {
    }

    public PickupRequest(int requestId, int donationId, int requesterId, int requestedQuantity,
                         LocalDateTime requestTime, RequestStatus status) {
        this.requestId = requestId;
        this.donationId = donationId;
        this.requesterId = requesterId;
        this.requestedQuantity = requestedQuantity;
        this.requestTime = requestTime;
        this.status = status;
    }

    public PickupRequest(int donationId, int requesterId, int requestedQuantity, String remarks) {
        this.donationId = donationId;
        this.requesterId = requesterId;
        this.requestedQuantity = requestedQuantity;
        this.remarks = remarks;
        this.requestTime = LocalDateTime.now();
        this.status = RequestStatus.PENDING;
    }

    public int getRequestId() {
        return requestId;
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
    }

    public int getDonationId() {
        return donationId;
    }

    public void setDonationId(int donationId) {
        this.donationId = donationId;
    }

    public int getRequesterId() {
        return requesterId;
    }

    public void setRequesterId(int requesterId) {
        this.requesterId = requesterId;
    }

    public int getRequestedQuantity() {
        return requestedQuantity;
    }

    public void setRequestedQuantity(int requestedQuantity) {
        this.requestedQuantity = requestedQuantity;
    }

    public LocalDateTime getRequestTime() {
        return requestTime;
    }

    public void setRequestTime(LocalDateTime requestTime) {
        this.requestTime = requestTime;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public String getFoodName() {
        return foodName;
    }

    public void setFoodName(String foodName) {
        this.foodName = foodName;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public void setRequesterName(String requesterName) {
        this.requesterName = requesterName;
    }

    public int getProviderId() {
        return providerId;
    }

    public void setProviderId(int providerId) {
        this.providerId = providerId;
    }

    @Override
    public String toString() {
        return String.format(
            "Request #%d [Status: %s] | Donation #%d (%s) | Requester: %s (ID: %d) | Qty: %d | Time: %s",
            requestId, status, donationId, (foodName != null ? foodName : "Donation"),
            (requesterName != null ? requesterName : "User"), requesterId, requestedQuantity,
            requestTime
        );
    }
}
