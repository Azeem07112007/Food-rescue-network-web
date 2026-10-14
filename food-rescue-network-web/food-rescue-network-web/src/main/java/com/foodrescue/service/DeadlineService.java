package com.foodrescue.service;

import com.foodrescue.config.DBConnection;
import com.foodrescue.model.FoodDonation;
import com.foodrescue.model.PickupRequest;
import com.foodrescue.model.enums.AlertType;
import com.foodrescue.model.enums.DonationStatus;
import com.foodrescue.model.enums.RequestStatus;
import com.foodrescue.repository.DonationRepository;
import com.foodrescue.repository.PickupRequestRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service handling periodic and event-triggered deadline checks, expiration transitions,
 * and approaching deadline notifications on the existing schema.
 */
public class DeadlineService {

    private static final Logger LOGGER = Logger.getLogger(DeadlineService.class.getName());

    private final DonationRepository donationRepository;
    private final PickupRequestRepository requestRepository;
    private final FoodAlertService alertService;
    private final Clock clock;

    public DeadlineService(DonationRepository donationRepository, PickupRequestRepository requestRepository,
                           FoodAlertService alertService, Clock clock) {
        this.donationRepository = donationRepository;
        this.requestRepository = requestRepository;
        this.alertService = alertService;
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
    }

    public DeadlineService(DonationRepository donationRepository, PickupRequestRepository requestRepository,
                           FoodAlertService alertService) {
        this(donationRepository, requestRepository, alertService, Clock.systemDefaultZone());
    }

    public DeadlineService() {
        this(new DonationRepository(), new PickupRequestRepository(), new FoodAlertService(), Clock.systemDefaultZone());
    }

    /**
     * Scans for donations whose pickup deadline has passed and updates statuses consistently.
     * Transaction-safe. Avoids overwriting COLLECTED or CANCELLED statuses.
     * Preserves approved reservation records without fabricating completed pickups.
     *
     * @return count of expired donations processed
     */
    public int checkAndExpireDonations() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<FoodDonation> expiredDonations;
        try {
            expiredDonations = donationRepository.findExpiredDonations(now);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to query expired donations", e);
            return 0;
        }

        int count = 0;
        for (FoodDonation donation : expiredDonations) {
            Connection conn = null;
            try {
                conn = DBConnection.getConnection();
                conn.setAutoCommit(false);

                // Row-lock donation
                FoodDonation current = donationRepository.findByIdForUpdate(conn, donation.getDonationId())
                        .orElse(null);

                if (current == null || current.getStatus() == DonationStatus.CANCELLED ||
                    current.getStatus() == DonationStatus.COLLECTED ||
                    current.getStatus() == DonationStatus.EXPIRED) {
                    conn.rollback();
                    continue;
                }

                int approvedCount = requestRepository.sumApprovedQuantityForDonation(conn, current.getDonationId());

                // If no active approved reservations, set remaining = 0 and status = EXPIRED
                if (approvedCount == 0) {
                    donationRepository.updateRemainingQuantityAndStatus(conn, current.getDonationId(),
                            0, DonationStatus.EXPIRED);
                } else {
                    // Approved reservations exist. Set remaining = 0 to prevent new requests,
                    // but preserve status so parties can still complete collection or coordinate.
                    donationRepository.updateRemainingQuantityAndStatus(conn, current.getDonationId(),
                            0, current.getStatus());
                }

                conn.commit();
                count++;

                // Alert provider
                String alertMsg = String.format("Donation #%d ('%s') pickup deadline (%s) has passed. Remaining portions set to 0. Approved reservations: %d.",
                        current.getDonationId(), current.getFoodName(), current.getPickupDeadline(), approvedCount);
                alertService.sendAlert(current.getProviderId(), current.getDonationId(), AlertType.DONATION_EXPIRED, alertMsg);

                // If reservations exist, notify approved requesters to collect urgently or coordinate
                if (approvedCount > 0) {
                    List<PickupRequest> requests = requestRepository.findByDonationId(current.getDonationId());
                    for (PickupRequest req : requests) {
                        if (req.getStatus() == RequestStatus.APPROVED) {
                            alertService.sendAlert(
                                    req.getRequesterId(),
                                    current.getDonationId(),
                                    AlertType.DEADLINE_APPROACHING,
                                    String.format("URGENT: Pickup deadline for donation #%d ('%s') has passed! Please coordinate immediate collection or cancellation.",
                                            current.getDonationId(), current.getFoodName())
                            );
                        }
                    }
                }

            } catch (SQLException e) {
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException ignored) {}
                }
                LOGGER.log(Level.WARNING, "Error processing expiration for donation #" + donation.getDonationId(), e);
            } finally {
                if (conn != null) {
                    try {
                        conn.setAutoCommit(true);
                        conn.close();
                    } catch (SQLException ignored) {}
                }
            }
        }
        return count;
    }

    /**
     * Checks for donations approaching their pickup deadline within the warning threshold (e.g. 2 hours)
     * and generates deduplicated alerts for providers and requesters.
     *
     * @param hoursThreshold hours before deadline to trigger warning
     * @return count of alerts sent
     */
    public int checkApproachingDeadlines(int hoursThreshold) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime threshold = now.plusHours(hoursThreshold);
        List<FoodDonation> approaching;
        try {
            approaching = donationRepository.findApproachingDeadlineDonations(now, threshold);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to query approaching deadlines: " + e.getMessage(), e);
            return 0;
        }

        int alertsSent = 0;
        for (FoodDonation donation : approaching) {
            String msg = String.format("REMINDER: Pickup deadline for donation #%d ('%s') is at %s.",
                    donation.getDonationId(), donation.getFoodName(), donation.getPickupDeadline());

            // Alert provider
            if (alertService.sendAlert(donation.getProviderId(), donation.getDonationId(), AlertType.DEADLINE_APPROACHING, msg)) {
                alertsSent++;
            }

            // Alert any requesters with approved pickups
            try {
                List<PickupRequest> reqs = requestRepository.findByDonationId(donation.getDonationId());
                for (PickupRequest r : reqs) {
                    if (r.getStatus() == RequestStatus.APPROVED) {
                        String reqMsg = String.format("Pickup reminder: Your approved pickup for '%s' at %s expires at %s!",
                                donation.getFoodName(), donation.getPickupLocation(), donation.getPickupDeadline());
                        if (alertService.sendAlert(r.getRequesterId(), donation.getDonationId(), AlertType.DEADLINE_APPROACHING, reqMsg)) {
                            alertsSent++;
                        }
                    }
                }
            } catch (SQLException ignored) {}
        }
        return alertsSent;
    }

    /**
     * Convenient trigger to run all deadline-related maintenance tasks at startup or on demand.
     */
    public void runAllDeadlineChecks() {
        int expired = checkAndExpireDonations();
        int warnings = checkApproachingDeadlines(2);
        LOGGER.info(String.format("Deadline checks completed: %d donations expired, %d warning alerts sent.", expired, warnings));
    }
}
