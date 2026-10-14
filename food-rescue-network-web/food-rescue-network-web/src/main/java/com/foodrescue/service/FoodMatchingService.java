package com.foodrescue.service;

import com.foodrescue.exception.BusinessRuleException;
import com.foodrescue.model.FoodDonation;
import com.foodrescue.repository.DonationRepository;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic rule-based matching algorithm to rank and recommend surplus food donations
 * to NGOs and volunteers based on location proximity, quantity needs, and time urgency.
 */
public class FoodMatchingService {

    private final DonationRepository donationRepository;
    private final Clock clock;

    public FoodMatchingService(DonationRepository donationRepository, Clock clock) {
        this.donationRepository = donationRepository;
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
    }

    public FoodMatchingService(DonationRepository donationRepository) {
        this(donationRepository, Clock.systemDefaultZone());
    }

    public FoodMatchingService() {
        this(new DonationRepository(), Clock.systemDefaultZone());
    }

    /**
     * Calculates deterministic match scores and ranks available food donations.
     */
    public List<MatchResult> findMatches(String desiredLocation, Integer targetQuantity, Double maxHoursWindow) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<FoodDonation> candidates;
        try {
            // Find all available donations that have not expired
            candidates = donationRepository.searchAvailable(null, null, null, now);
        } catch (SQLException e) {
            throw new BusinessRuleException("Database error loading candidates for matching: " + e.getMessage(), e);
        }

        List<MatchResult> results = new ArrayList<>();

        for (FoodDonation donation : candidates) {
            // Time window checks
            Duration remaining = Duration.between(now, donation.getPickupDeadline());
            double hoursLeft = remaining.toMinutes() / 60.0;
            if (hoursLeft <= 0) {
                continue; // expired
            }
            if (maxHoursWindow != null && hoursLeft > maxHoursWindow) {
                continue; // beyond requested search window
            }

            int locationScore = calculateLocationScore(desiredLocation, donation.getPickupLocation());
            int quantityScore = calculateQuantityScore(targetQuantity, donation.getAvailableQuantity());
            int urgencyScore = calculateUrgencyScore(hoursLeft);

            int totalScore = locationScore + quantityScore + urgencyScore;
            String reason = buildMatchReason(desiredLocation, targetQuantity, donation, locationScore, quantityScore, urgencyScore, hoursLeft);

            results.add(new MatchResult(donation, totalScore, reason, hoursLeft));
        }

        // Rank by total score descending, then by deadline ascending (most urgent first)
        results.sort(Comparator.comparingInt(MatchResult::getScore).reversed()
                .thenComparingDouble(MatchResult::getHoursRemaining));

        return results;
    }

    private int calculateLocationScore(String desiredLocation, String actualLocation) {
        if (desiredLocation == null || desiredLocation.trim().isEmpty()) {
            return 20; // neutral default when location is not filtered
        }
        String desired = desiredLocation.trim().toLowerCase();
        String actual = actualLocation != null ? actualLocation.trim().toLowerCase() : "";

        if (desired.equals(actual)) {
            return 35; // exact match
        }
        if (actual.contains(desired) || desired.contains(actual)) {
            return 25; // partial / neighborhood match
        }
        return 5; // different location
    }

    private int calculateQuantityScore(Integer targetQuantity, int availableQuantity) {
        if (targetQuantity == null || targetQuantity <= 0) {
            return Math.min(35, 15 + Math.min(availableQuantity, 100) / 5);
        }
        if (availableQuantity >= targetQuantity) {
            return 35; // fully meets demand
        }
        // Partial fulfillment
        double fraction = (double) availableQuantity / targetQuantity;
        return (int) Math.round(fraction * 25.0);
    }

    private int calculateUrgencyScore(double hoursLeft) {
        if (hoursLeft < 0.5) {
            return 8; // less than 30 mins: high risk of missed pickup
        }
        if (hoursLeft <= 3.0) {
            return 30; // 0.5h to 3h: Golden rescue window - maximum urgency to avoid food waste!
        }
        if (hoursLeft <= 8.0) {
            return 24; // moderate urgency
        }
        if (hoursLeft <= 24.0) {
            return 16; // manageable window
        }
        return 10; // long deadline
    }

    private String buildMatchReason(String desiredLocation, Integer targetQuantity, FoodDonation donation,
                                    int locScore, int qtyScore, int urgScore, double hoursLeft) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Score: %d/100 | ", locScore + qtyScore + urgScore));

        if (urgScore >= 25) {
            sb.append(String.format("HIGH URGENCY (%.1fh left - rescue soon!); ", hoursLeft));
        } else {
            sb.append(String.format("Window: %.1fh left; ", hoursLeft));
        }

        if (locScore >= 30) {
            sb.append("Exact location match; ");
        } else if (locScore >= 20) {
            sb.append("Nearby/partial location match; ");
        }

        if (targetQuantity != null && targetQuantity > 0) {
            if (donation.getAvailableQuantity() >= targetQuantity) {
                sb.append(String.format("Fully satisfies need (%d available >= %d requested).",
                        donation.getAvailableQuantity(), targetQuantity));
            } else {
                sb.append(String.format("Partial fulfillment (%d available / %d requested).",
                        donation.getAvailableQuantity(), targetQuantity));
            }
        } else {
            sb.append(String.format("Available: %d portions.", donation.getAvailableQuantity()));
        }

        return sb.toString();
    }

    /**
     * DTO containing a ranked match result with scoring breakdown and readable justification.
     */
    public static class MatchResult {
        private final FoodDonation donation;
        private final int score;
        private final String matchReason;
        private final double hoursRemaining;

        public MatchResult(FoodDonation donation, int score, String matchReason, double hoursRemaining) {
            this.donation = donation;
            this.score = score;
            this.matchReason = matchReason;
            this.hoursRemaining = hoursRemaining;
        }

        public FoodDonation getDonation() {
            return donation;
        }

        public int getScore() {
            return score;
        }

        public String getMatchReason() {
            return matchReason;
        }

        public double getHoursRemaining() {
            return hoursRemaining;
        }

        @Override
        public String toString() {
            return String.format("[%d pts] %s\n      Reason: %s",
                    score, donation.toString(), matchReason);
        }
    }
}
