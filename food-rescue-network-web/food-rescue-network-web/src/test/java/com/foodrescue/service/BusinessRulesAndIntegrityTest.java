package com.foodrescue.service;

import com.foodrescue.exception.BusinessRuleException;
import com.foodrescue.model.FoodDonation;
import com.foodrescue.model.PickupRequest;
import com.foodrescue.model.User;
import com.foodrescue.model.enums.DonationStatus;
import com.foodrescue.model.enums.RequestStatus;
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

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Business Rules, Role Validation, Transactions, and Integrity Tests")
class BusinessRulesAndIntegrityTest {

    private UserService userService;
    private DonationService donationService;
    private PickupRequestService pickupRequestService;
    private DeadlineService deadlineService;
    private FoodAlertService alertService;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        // Initialize in-memory test database schema matching existing MySQL schema
        TestDbHelper.setupTestDatabase();

        Instant fixedInstant = Instant.parse("2026-10-15T12:00:00Z");
        fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));

        UserRepository userRepo = new UserRepository();
        DonationRepository donationRepo = new DonationRepository();
        PickupRequestRepository requestRepo = new PickupRequestRepository();
        FoodAlertRepository alertRepo = new FoodAlertRepository();

        alertService = new FoodAlertService(alertRepo);
        userService = new UserService(userRepo);
        donationService = new DonationService(donationRepo, userRepo, alertService, fixedClock);
        pickupRequestService = new PickupRequestService(requestRepo, donationRepo, userRepo, alertService, fixedClock);
        deadlineService = new DeadlineService(donationRepo, requestRepo, alertService, fixedClock);
    }

    @Test
    @DisplayName("Feature 1: Duplicate email registration should be rejected with BusinessRuleException")
    void testDuplicateEmailRejection() {
        userService.registerUser("Green Kitchen", "green@kitchen.org", "9876543210", UserType.RESTAURANT, "Coimbatore");

        assertThrows(BusinessRuleException.class, () ->
                userService.registerUser("Duplicate Green", "GREEN@kitchen.org", "1122334455", UserType.RESTAURANT, "Coimbatore")
        );
    }

    @Test
    @DisplayName("Feature 2: Only valid provider roles may publish food donations")
    void testRoleEnforcementOnPublishing() {
        User ngo = userService.registerUser("Helping Hands NGO", "help@ngo.org", "9988776655", UserType.NGO, "Coimbatore");
        LocalDateTime now = LocalDateTime.now(fixedClock);

        // NGO attempting to publish donation must fail
        assertThrows(BusinessRuleException.class, () ->
                donationService.publishDonation(ngo.getUserId(), "Rice & Curry", "Cooked Meal", 50,
                        "City Hall", now.minusHours(1), now.plusHours(4))
        );
    }

    @Test
    @DisplayName("Feature 2: Publication should initialize quantity invariants and AVAILABLE status")
    void testPublishDonationInvariants() {
        User restaurant = userService.registerUser("Bistro 42", "bistro@food.com", "555-1234", UserType.RESTAURANT, "Chennai");
        LocalDateTime now = LocalDateTime.now(fixedClock);

        FoodDonation d = donationService.publishDonation(
                restaurant.getUserId(), "Veg Meals", "Cooked Meal", 100,
                "Bistro Center", now.minusHours(1), now.plusHours(5)
        );

        assertNotNull(d);
        assertEquals(100, d.getQuantity());
        assertEquals(100, d.getRemainingQuantity());
        assertEquals(DonationStatus.AVAILABLE, d.getStatus());
    }

    @Test
    @DisplayName("Feature 4: Only NGOs and Volunteers may create pickup requests; Providers cannot")
    void testRoleEnforcementOnPickupRequest() {
        User restaurant = userService.registerUser("Pasta Palace", "pasta@palace.com", "555-4321", UserType.RESTAURANT, "Coimbatore");
        User anotherProvider = userService.registerUser("City Hostel", "hostel@city.org", "555-9999", UserType.HOSTEL, "Coimbatore");
        LocalDateTime now = LocalDateTime.now(fixedClock);

        FoodDonation donation = donationService.publishDonation(
                restaurant.getUserId(), "Pasta Trays", "Cooked Meal", 40,
                "Pasta Palace", now.minusHours(1), now.plusHours(4)
        );

        // Provider cannot request
        assertThrows(BusinessRuleException.class, () ->
                pickupRequestService.createPickupRequest(donation.getDonationId(), anotherProvider.getUserId(), 20, "For students")
        );
    }

    @Test
    @DisplayName("Feature 4: Pending requests must NOT deduct available quantity until approved")
    void testPendingRequestDoesNotDeductInventory() {
        User restaurant = userService.registerUser("Grand Buffet", "buffet@grand.com", "555-1111", UserType.RESTAURANT, "Coimbatore");
        User ngo = userService.registerUser("Care Foundation", "care@foundation.org", "555-2222", UserType.NGO, "Coimbatore");
        LocalDateTime now = LocalDateTime.now(fixedClock);

        FoodDonation donation = donationService.publishDonation(
                restaurant.getUserId(), "Sandwiches", "Bakery", 50,
                "Grand Buffet", now.minusHours(1), now.plusHours(3)
        );

        PickupRequest req = pickupRequestService.createPickupRequest(donation.getDonationId(), ngo.getUserId(), 30, "Urgent meals");
        assertEquals(RequestStatus.PENDING, req.getStatus());

        // Invariant: remaining quantity remains 50
        FoodDonation updated = donationService.getDonationById(donation.getDonationId());
        assertEquals(50, updated.getRemainingQuantity());
    }

    @Test
    @DisplayName("Feature 4: Requesting more than available quantity must be rejected")
    void testInsufficientQuantityRejection() {
        User restaurant = userService.registerUser("Spice Garden", "spice@garden.com", "555-7777", UserType.RESTAURANT, "Coimbatore");
        User volunteer = userService.registerUser("Sam Volunteer", "sam@volunteer.org", "555-8888", UserType.VOLUNTEER, "Coimbatore");
        LocalDateTime now = LocalDateTime.now(fixedClock);

        FoodDonation donation = donationService.publishDonation(
                restaurant.getUserId(), "Meals", "Cooked", 25,
                "Spice Garden", now.minusHours(1), now.plusHours(4)
        );

        assertThrows(BusinessRuleException.class, () ->
                pickupRequestService.createPickupRequest(donation.getDonationId(), volunteer.getUserId(), 30, "Need all")
        );
    }

    @Test
    @DisplayName("Feature 5: Approval reserves quantity, updates status, and prevents overbooking")
    void testApprovalAndOverbookingPrevention() {
        User provider = userService.registerUser("Royal Canteen", "royal@canteen.edu", "555-3333", UserType.CANTEEN, "Chennai");
        User ngo1 = userService.registerUser("FeedAll NGO", "feed@all.org", "555-4444", UserType.NGO, "Chennai");
        User ngo2 = userService.registerUser("Hope NGO", "hope@ngo.org", "555-5555", UserType.NGO, "Chennai");
        LocalDateTime now = LocalDateTime.now(fixedClock);

        FoodDonation donation = donationService.publishDonation(
                provider.getUserId(), "Lunch Boxes", "Cooked Meal", 50,
                "Campus Canteen", now.minusHours(1), now.plusHours(4)
        );

        PickupRequest req1 = pickupRequestService.createPickupRequest(donation.getDonationId(), ngo1.getUserId(), 40, "Need 40");
        PickupRequest req2 = pickupRequestService.createPickupRequest(donation.getDonationId(), ngo2.getUserId(), 20, "Need 20");

        // Provider approves req1 (takes 40 out of 50)
        pickupRequestService.approveRequest(req1.getRequestId(), provider.getUserId());

        FoodDonation afterReq1 = donationService.getDonationById(donation.getDonationId());
        assertEquals(10, afterReq1.getRemainingQuantity());

        // Req2 needs 20, but only 10 is remaining. Approving req2 MUST fail!
        assertThrows(BusinessRuleException.class, () ->
                pickupRequestService.approveRequest(req2.getRequestId(), provider.getUserId())
        );
    }

    @Test
    @DisplayName("Feature 8: Cancelling an approved request must restore available quantity")
    void testCancellationRestoresQuantity() {
        User provider = userService.registerUser("Hotel Sunrise", "sunrise@hotel.com", "555-6666", UserType.RESTAURANT, "Coimbatore");
        User ngo = userService.registerUser("Relief NGO", "relief@ngo.org", "555-7778", UserType.NGO, "Coimbatore");
        LocalDateTime now = LocalDateTime.now(fixedClock);

        FoodDonation donation = donationService.publishDonation(
                provider.getUserId(), "Breakfast Pastries", "Bakery", 60,
                "Sunrise Lobby", now.minusHours(1), now.plusHours(6)
        );

        PickupRequest req = pickupRequestService.createPickupRequest(donation.getDonationId(), ngo.getUserId(), 40, "Pickup at 10");
        pickupRequestService.approveRequest(req.getRequestId(), provider.getUserId());

        FoodDonation reservedState = donationService.getDonationById(donation.getDonationId());
        assertEquals(20, reservedState.getRemainingQuantity());

        // Requester cancels request
        pickupRequestService.cancelRequest(req.getRequestId(), ngo.getUserId());

        FoodDonation restoredState = donationService.getDonationById(donation.getDonationId());
        assertEquals(60, restoredState.getRemainingQuantity());
        assertEquals(DonationStatus.AVAILABLE, restoredState.getStatus());
    }

    @Test
    @DisplayName("Feature 8: Completing pickup marks donation COLLECTED when all portions are completed")
    void testPickupCompletion() {
        User provider = userService.registerUser("City Bakery", "bakery@city.com", "555-9090", UserType.RESTAURANT, "Coimbatore");
        User ngo = userService.registerUser("Youth Mission", "youth@mission.org", "555-9091", UserType.NGO, "Coimbatore");
        LocalDateTime now = LocalDateTime.now(fixedClock);

        FoodDonation donation = donationService.publishDonation(
                provider.getUserId(), "Artisan Bread", "Bakery", 30,
                "City Bakery Counter", now.minusHours(1), now.plusHours(4)
        );

        PickupRequest req = pickupRequestService.createPickupRequest(donation.getDonationId(), ngo.getUserId(), 30, "All 30 bread loaves");
        pickupRequestService.approveRequest(req.getRequestId(), provider.getUserId());

        // Complete pickup
        pickupRequestService.completePickup(req.getRequestId(), provider.getUserId());

        FoodDonation collectedDonation = donationService.getDonationById(donation.getDonationId());
        assertEquals(0, collectedDonation.getRemainingQuantity());
        assertEquals(DonationStatus.COLLECTED, collectedDonation.getStatus());

        PickupRequest completedReq = pickupRequestService.getRequestById(req.getRequestId());
        assertEquals(RequestStatus.COMPLETED, completedReq.getStatus());
    }

    @Test
    @DisplayName("Feature 13: Deadline service expires past-deadline donations without deleting reservations")
    void testDeadlineExpirationBehavior() {
        User provider = userService.registerUser("Night Diner", "diner@night.com", "555-1212", UserType.RESTAURANT, "Coimbatore");
        LocalDateTime now = LocalDateTime.now(fixedClock);

        // Donation expiring in 1 hour
        FoodDonation d = donationService.publishDonation(
                provider.getUserId(), "Soup Portions", "Cooked", 40,
                "Diner Kitchen", now.minusHours(2), now.plusHours(1)
        );

        // Move time forward 2 hours (past deadline)
        Instant laterInstant = fixedClock.instant().plus(java.time.Duration.ofHours(2));
        Clock laterClock = Clock.fixed(laterInstant, ZoneId.of("UTC"));
        DeadlineService laterDeadlineService = new DeadlineService(new DonationRepository(), new PickupRequestRepository(), alertService, laterClock);

        int expiredCount = laterDeadlineService.checkAndExpireDonations();
        assertTrue(expiredCount >= 1);

        FoodDonation expiredDonation = donationService.getDonationById(d.getDonationId());
        assertEquals(DonationStatus.EXPIRED, expiredDonation.getStatus());
        assertEquals(0, expiredDonation.getRemainingQuantity());
    }
}
