package com.foodrescue.service;

import com.foodrescue.model.FoodDonation;
import com.foodrescue.model.User;
import com.foodrescue.model.enums.UserType;
import com.foodrescue.repository.DonationRepository;
import com.foodrescue.repository.FoodAlertRepository;
import com.foodrescue.repository.PickupRequestRepository;
import com.foodrescue.repository.UserRepository;
import com.foodrescue.util.TestDbHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Feature 6: Smart Food Matching Service Tests")
class FoodMatchingServiceTest {

    private FoodMatchingService matchingService;
    private DonationService donationService;
    private UserService userService;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        TestDbHelper.setupTestDatabase();

        Instant fixedInstant = Instant.parse("2026-10-15T12:00:00Z");
        fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));

        UserRepository userRepo = new UserRepository();
        DonationRepository donationRepo = new DonationRepository();
        PickupRequestRepository requestRepo = new PickupRequestRepository();
        FoodAlertRepository alertRepo = new FoodAlertRepository();
        FoodAlertService alertService = new FoodAlertService(alertRepo);

        userService = new UserService(userRepo);
        donationService = new DonationService(donationRepo, userRepo, alertService, fixedClock);
        matchingService = new FoodMatchingService(donationRepo, fixedClock);
    }

    @Test
    @DisplayName("Matching should prioritize exact location, sufficient quantity, and golden urgency window")
    void testMatchingScoringAndRanking() {
        User provider = userService.registerUser("Campus Bistro", "bistro@campus.edu", "555-0101", UserType.RESTAURANT);
        LocalDateTime now = LocalDateTime.now(fixedClock);

        // Candidate A: Urgent (2h left), Downtown, exactly 50 portions
        donationService.publishDonation(
                provider.getUserId(), "Curry & Rice", "Cooked", 50,
                "Downtown Center", now.minusHours(1), now.plusHours(2)
        );

        // Candidate B: Not urgent (20h left), Far Suburb, 10 portions
        donationService.publishDonation(
                provider.getUserId(), "Buns", "Bakery", 10,
                "Far Suburb 10", now.minusHours(1), now.plusHours(20)
        );

        // Candidate C: Expired (0h left - published in past) - should not match
        // Note: publishDonation rejects expired, so it won't be candidate

        // Search for Downtown, 40 portions
        List<FoodMatchingService.MatchResult> matches = matchingService.findMatches("Downtown Center", 40, null);

        assertFalse(matches.isEmpty());
        assertEquals(2, matches.size());

        // Top match must be Candidate A because it has exact location, high urgency, and meets quantity
        FoodMatchingService.MatchResult topMatch = matches.get(0);
        assertEquals("Curry & Rice", topMatch.getDonation().getFoodName());
        assertTrue(topMatch.getScore() > matches.get(1).getScore());
        assertTrue(topMatch.getMatchReason().contains("HIGH URGENCY") || topMatch.getMatchReason().contains("Exact location"));
    }
}
