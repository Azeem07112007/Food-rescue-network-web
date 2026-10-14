package com.foodrescue.service;

import com.foodrescue.config.DBConnection;
import com.foodrescue.exception.BusinessRuleException;
import com.foodrescue.exception.NotFoundException;
import com.foodrescue.model.FoodDonation;
import com.foodrescue.model.PickupRequest;
import com.foodrescue.model.User;
import com.foodrescue.model.enums.AlertType;
import com.foodrescue.model.enums.DonationStatus;
import com.foodrescue.model.enums.RequestStatus;
import com.foodrescue.repository.DonationRepository;
import com.foodrescue.repository.PickupRequestRepository;
import com.foodrescue.repository.UserRepository;
import com.foodrescue.util.InputValidator;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service managing pickup requests with transaction-safe approvals, cancellations, and completions
 * on the existing 'food_donations' (remaining_quantity) and 'pickup_requests' tables.
 */
public class PickupRequestService {

    private static final Logger LOGGER = Logger.getLogger(PickupRequestService.class.getName());

    private final PickupRequestRepository requestRepository;
    private final DonationRepository donationRepository;
    private final UserRepository userRepository;
    private final FoodAlertService alertService;
    private final Clock clock;

    public PickupRequestService(PickupRequestRepository requestRepository,
                                DonationRepository donationRepository,
                                UserRepository userRepository,
                                FoodAlertService alertService,
                                Clock clock) {
        this.requestRepository = requestRepository;
        this.donationRepository = donationRepository;
        this.userRepository = userRepository;
        this.alertService = alertService;
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
    }

    public PickupRequestService(PickupRequestRepository requestRepository,
                                DonationRepository donationRepository,
                                UserRepository userRepository,
                                FoodAlertService alertService) {
        this(requestRepository, donationRepository, userRepository, alertService, Clock.systemDefaultZone());
    }

    public PickupRequestService() {
        this(new PickupRequestRepository(), new DonationRepository(), new UserRepository(), new FoodAlertService(), Clock.systemDefaultZone());
    }

    /**
     * Feature 4 — Request Food Pickup
     * Allows only NGOs and Volunteers to create pickup requests in PENDING status.
     * Does NOT deduct inventory until approved.
     */
    public PickupRequest createPickupRequest(int donationId, int requesterId, int requestedQuantity, String remarks) {
        // 1. Role validation
        User requester;
        try {
            requester = userRepository.findById(requesterId)
                    .orElseThrow(() -> new NotFoundException("Requester user not found with ID: " + requesterId));
        } catch (SQLException e) {
            throw new BusinessRuleException("Database error verifying requester: " + e.getMessage(), e);
        }

        if (!requester.getUserType().isRequester()) {
            throw new BusinessRuleException("Role violation: User #" + requesterId + " has role '" +
                    requester.getUserType() + "'. Only NGOs and Volunteers can create food pickup requests.");
        }

        InputValidator.validatePositive(requestedQuantity, "Requested quantity");

        // 2. Donation eligibility validation
        FoodDonation donation;
        try {
            donation = donationRepository.findById(donationId)
                    .orElseThrow(() -> new NotFoundException("Food donation not found with ID: " + donationId));
        } catch (SQLException e) {
            throw new BusinessRuleException("Database error loading donation: " + e.getMessage(), e);
        }

        if (donation.getProviderId() == requesterId) {
            throw new BusinessRuleException("Business rule violation: A user cannot request their own food donation.");
        }

        if (donation.getStatus() == DonationStatus.CANCELLED ||
            donation.getStatus() == DonationStatus.COLLECTED ||
            donation.getStatus() == DonationStatus.EXPIRED) {
            throw new BusinessRuleException("Cannot request donation: Current status is " + donation.getStatus());
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (!donation.getPickupDeadline().isAfter(now)) {
            throw new BusinessRuleException("Cannot request donation: The pickup deadline (" +
                    donation.getPickupDeadline() + ") has already passed.");
        }

        if (requestedQuantity > donation.getRemainingQuantity()) {
            throw new BusinessRuleException("Insufficient quantity: Requested " + requestedQuantity +
                    " but only " + donation.getRemainingQuantity() + " are currently available.");
        }

        // 3. Save pending request (does not reduce remaining_quantity yet)
        PickupRequest req = new PickupRequest(donationId, requesterId, requestedQuantity, remarks);
        req.setRequestTime(now);
        req.setStatus(RequestStatus.PENDING);

        try {
            PickupRequest saved = requestRepository.save(req);

            // Alert provider of the incoming request
            alertService.sendAlert(
                    donation.getProviderId(),
                    donationId,
                    AlertType.REQUEST_SUBMITTED,
                    String.format("New pickup request #%d for '%s' (%d portions) from %s (%s).",
                            saved.getRequestId(), donation.getFoodName(), requestedQuantity,
                            requester.getUserName(), requester.getUserType())
            );

            return saved;
        } catch (SQLException e) {
            throw new BusinessRuleException("Database error saving pickup request: " + e.getMessage(), e);
        }
    }

    /**
     * Feature 5 — Approve Pickup Request
     * Atomically validates available quantity, reserves the food by reducing remaining_quantity,
     * updates request status within an explicit database transaction using row locks (FOR UPDATE).
     */
    public void approveRequest(int requestId, int actingProviderId) {
        Connection conn = null;
        PickupRequest req;
        FoodDonation donation;

        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            // Row-lock request
            req = requestRepository.findByIdForUpdate(conn, requestId)
                    .orElseThrow(() -> new NotFoundException("Pickup request not found with ID: " + requestId));

            if (req.getStatus() != RequestStatus.PENDING) {
                throw new BusinessRuleException("Request cannot be approved. Current status is already " + req.getStatus());
            }

            // Row-lock donation
            donation = donationRepository.findByIdForUpdate(conn, req.getDonationId())
                    .orElseThrow(() -> new NotFoundException("Associated donation not found with ID: " + req.getDonationId()));

            // Ownership check
            if (donation.getProviderId() != actingProviderId) {
                throw new BusinessRuleException("Unauthorized: Only provider #" + donation.getProviderId() +
                        " who owns this donation can approve requests. (Acting user: #" + actingProviderId + ")");
            }

            LocalDateTime now = LocalDateTime.now(clock);
            if (!donation.getPickupDeadline().isAfter(now)) {
                throw new BusinessRuleException("Cannot approve request: Donation pickup deadline has passed.");
            }

            // Overbooking prevention check
            if (donation.getRemainingQuantity() < req.getRequestedQuantity()) {
                throw new BusinessRuleException("Cannot approve request: Requested quantity (" +
                        req.getRequestedQuantity() + ") exceeds available remaining balance (" + donation.getRemainingQuantity() + ").");
            }

            // Update remaining_quantity and status atomically
            int newRemaining = donation.getRemainingQuantity() - req.getRequestedQuantity();
            DonationStatus newStatus = (newRemaining == 0) ? DonationStatus.RESERVED : donation.getStatus();

            donationRepository.updateRemainingQuantityAndStatus(conn, donation.getDonationId(), newRemaining, newStatus);
            requestRepository.updateStatus(conn, requestId, RequestStatus.APPROVED);

            conn.commit();
        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw new BusinessRuleException("Database error approving pickup request: " + e.getMessage(), e);
        } catch (Exception e) {
            rollbackQuietly(conn);
            throw e;
        } finally {
            resetAutoCommitAndClose(conn);
        }

        // Send alert to requester
        alertService.sendAlert(
                req.getRequesterId(),
                req.getDonationId(),
                AlertType.REQUEST_APPROVED,
                String.format("Good news! Your pickup request #%d for '%s' (%d portions) has been APPROVED by %s. Location: %s. Deadline: %s.",
                        requestId, donation.getFoodName(), req.getRequestedQuantity(),
                        donation.getProviderName() != null ? donation.getProviderName() : "Provider",
                        donation.getPickupLocation(), donation.getPickupDeadline())
        );
    }

    /**
     * Feature 5 — Reject Pickup Request
     */
    public void rejectRequest(int requestId, int actingProviderId, String reason) {
        PickupRequest req = getRequestById(requestId);
        FoodDonation donation;
        try {
            donation = donationRepository.findById(req.getDonationId())
                    .orElseThrow(() -> new NotFoundException("Donation not found with ID: " + req.getDonationId()));
        } catch (SQLException e) {
            throw new BusinessRuleException("Database error retrieving donation: " + e.getMessage(), e);
        }

        if (donation.getProviderId() != actingProviderId) {
            throw new BusinessRuleException("Unauthorized: Only the provider who owns this donation can reject requests.");
        }

        if (req.getStatus() != RequestStatus.PENDING) {
            throw new BusinessRuleException("Cannot reject request: Current status is " + req.getStatus() + ". Only PENDING requests can be rejected.");
        }

        try {
            requestRepository.updateStatus(requestId, RequestStatus.REJECTED);
        } catch (SQLException e) {
            throw new BusinessRuleException("Database error rejecting request: " + e.getMessage(), e);
        }

        alertService.sendAlert(
                req.getRequesterId(),
                req.getDonationId(),
                AlertType.REQUEST_REJECTED,
                String.format("Your pickup request #%d for '%s' was rejected. Reason: %s",
                        requestId, donation.getFoodName(), (reason != null && !reason.isEmpty() ? reason : "Unavailable"))
        );
    }

    /**
     * Feature 8 — Cancel Pickup Request
     * Allows requester to cancel PENDING or APPROVED requests.
     * If request was APPROVED, atomically restores remaining quantity in an explicit transaction.
     */
    public void cancelRequest(int requestId, int actingRequesterId) {
        Connection conn = null;
        PickupRequest req;
        FoodDonation donation;

        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            req = requestRepository.findByIdForUpdate(conn, requestId)
                    .orElseThrow(() -> new NotFoundException("Pickup request not found with ID: " + requestId));

            if (req.getRequesterId() != actingRequesterId) {
                throw new BusinessRuleException("Unauthorized: Only the requester (#" + req.getRequesterId() +
                        ") who created this request can cancel it.");
            }

            if (req.getStatus() != RequestStatus.PENDING && req.getStatus() != RequestStatus.APPROVED) {
                throw new BusinessRuleException("Cannot cancel request: Current status is " + req.getStatus() +
                        ". Only PENDING or APPROVED requests can be cancelled.");
            }

            donation = donationRepository.findByIdForUpdate(conn, req.getDonationId())
                    .orElseThrow(() -> new NotFoundException("Donation not found with ID: " + req.getDonationId()));

            // If request was APPROVED, restore quantity if deadline has not passed
            if (req.getStatus() == RequestStatus.APPROVED) {
                LocalDateTime now = LocalDateTime.now(clock);
                int newRemaining = donation.getRemainingQuantity();
                DonationStatus newStatus = donation.getStatus();

                if (donation.getPickupDeadline().isAfter(now)) {
                    newRemaining = donation.getRemainingQuantity() + req.getRequestedQuantity();
                    if (newRemaining > donation.getQuantity()) {
                        newRemaining = donation.getQuantity();
                    }
                    newStatus = DonationStatus.AVAILABLE;
                }

                donationRepository.updateRemainingQuantityAndStatus(conn, donation.getDonationId(), newRemaining, newStatus);
            }

            requestRepository.updateStatus(conn, requestId, RequestStatus.CANCELLED);
            conn.commit();
        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw new BusinessRuleException("Database error cancelling pickup request: " + e.getMessage(), e);
        } catch (Exception e) {
            rollbackQuietly(conn);
            throw e;
        } finally {
            resetAutoCommitAndClose(conn);
        }

        // Notify provider of cancellation
        alertService.sendAlert(
                donation.getProviderId(),
                donation.getDonationId(),
                AlertType.REQUEST_CANCELLED,
                String.format("Pickup request #%d for '%s' (%d portions) was cancelled by the requester.",
                        requestId, donation.getFoodName(), req.getRequestedQuantity())
        );
    }

    /**
     * Feature 8 — Complete Pickup
     * Provider marks an approved request as collected.
     * Transitions donation to COLLECTED when remaining quantity is 0 and all approved requests are finished.
     */
    public void completePickup(int requestId, int actingProviderId) {
        Connection conn = null;
        PickupRequest req;
        FoodDonation donation;

        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            req = requestRepository.findByIdForUpdate(conn, requestId)
                    .orElseThrow(() -> new NotFoundException("Pickup request not found with ID: " + requestId));

            donation = donationRepository.findByIdForUpdate(conn, req.getDonationId())
                    .orElseThrow(() -> new NotFoundException("Donation not found with ID: " + req.getDonationId()));

            if (donation.getProviderId() != actingProviderId) {
                throw new BusinessRuleException("Unauthorized: Only the provider who owns this donation can mark pickups as completed.");
            }

            if (req.getStatus() != RequestStatus.APPROVED) {
                throw new BusinessRuleException("Cannot complete pickup: Request must be in APPROVED status. Current status: " + req.getStatus());
            }

            // Mark this request COMPLETED
            requestRepository.updateStatus(conn, requestId, RequestStatus.COMPLETED);

            // Determine donation status based on remaining portions and other active approved requests
            boolean hasOtherApproved = requestRepository.hasOtherActiveApprovedRequests(conn, donation.getDonationId(), requestId);

            DonationStatus newStatus;
            if (donation.getRemainingQuantity() == 0 && !hasOtherApproved) {
                newStatus = DonationStatus.COLLECTED;
            } else if (donation.getRemainingQuantity() > 0) {
                newStatus = DonationStatus.AVAILABLE;
            } else {
                newStatus = DonationStatus.RESERVED;
            }

            donationRepository.updateStatus(conn, donation.getDonationId(), newStatus);
            conn.commit();
        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw new BusinessRuleException("Database error completing pickup: " + e.getMessage(), e);
        } catch (Exception e) {
            rollbackQuietly(conn);
            throw e;
        } finally {
            resetAutoCommitAndClose(conn);
        }

        // Notify requester
        alertService.sendAlert(
                req.getRequesterId(),
                donation.getDonationId(),
                AlertType.PICKUP_COMPLETED,
                String.format("Pickup request #%d for '%s' (%d portions) has been marked COMPLETED. Thank you for rescuing surplus food!",
                        requestId, donation.getFoodName(), req.getRequestedQuantity())
        );
    }

    public PickupRequest getRequestById(int requestId) {
        try {
            return requestRepository.findById(requestId)
                    .orElseThrow(() -> new NotFoundException("Pickup request not found with ID: " + requestId));
        } catch (SQLException e) {
            throw new BusinessRuleException("Database error retrieving request: " + e.getMessage(), e);
        }
    }

    public List<PickupRequest> getRequestsForProvider(int providerId) {
        try {
            return requestRepository.findByProviderId(providerId);
        } catch (SQLException e) {
            throw new BusinessRuleException("Database error retrieving provider requests: " + e.getMessage(), e);
        }
    }

    public List<PickupRequest> getRequestsForRequester(int requesterId) {
        try {
            return requestRepository.findByRequesterId(requesterId);
        } catch (SQLException e) {
            throw new BusinessRuleException("Database error retrieving requester requests: " + e.getMessage(), e);
        }
    }

    private void rollbackQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "Failed to rollback transaction", ex);
            }
        }
    }

    private void resetAutoCommitAndClose(Connection conn) {
        if (conn != null) {
            try {
                conn.setAutoCommit(true);
                conn.close();
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "Failed to reset autocommit and close connection", ex);
            }
        }
    }
}
