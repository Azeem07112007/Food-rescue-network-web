package com.foodrescue.controller;

import com.foodrescue.exception.BusinessRuleException;
import com.foodrescue.exception.ValidationException;
import com.foodrescue.model.FoodDonation;
import com.foodrescue.model.User;
import com.foodrescue.service.DonationService;
import com.foodrescue.service.FoodMatchingService;
import com.foodrescue.util.ConsoleInput;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Scanner;

/**
 * Controller handling food donation publishing, search filtering, and smart matching.
 */
public class DonationController {

    private final DonationService donationService;
    private final FoodMatchingService matchingService;

    public DonationController(DonationService donationService, FoodMatchingService matchingService) {
        this.donationService = donationService;
        this.matchingService = matchingService;
    }

    public DonationController() {
        this(new DonationService(), new FoodMatchingService());
    }

    /**
     * Feature 2 — Publish a food donation.
     */
    public void publishDonation(Scanner scanner, User actingUser) {
        if (actingUser == null) {
            System.out.println("\n[ERROR] No active user selected. Please select or register a provider user first (Menu Option 3).");
            return;
        }

        if (!actingUser.getUserType().isProvider()) {
            System.out.printf("\n[ERROR] Current user '%s' has role '%s'. Only providers (RESTAURANT, HOSTEL, CANTEEN, EVENT_ORGANIZER) can publish donations.%n",
                    actingUser.getUserName(), actingUser.getUserType());
            return;
        }

        System.out.println("\n--- Publish Surplus Food Donation ---");
        System.out.printf("Publishing as: %s (ID: %d, Role: %s)%n", actingUser.getUserName(), actingUser.getUserId(), actingUser.getUserType());

        String foodName = ConsoleInput.readString(scanner, "Enter Food Item Name: ", true);
        String category = ConsoleInput.readString(scanner, "Enter Category (e.g. Cooked Meal, Bakery, Produce, Packaged): ", true);
        int quantity = ConsoleInput.readInt(scanner, "Enter Quantity (portions/servings/kg): ", 1, 100000);
        String location = ConsoleInput.readString(scanner, "Enter Pickup Address / Location: ", true);

        System.out.println("\nSpecify Availability Window:");
        LocalDateTime availableFrom = ConsoleInput.readDateTime(scanner, "Available From");
        LocalDateTime pickupDeadline = ConsoleInput.readDateTime(scanner, "Pickup Deadline");

        try {
            FoodDonation donation = donationService.publishDonation(
                    actingUser.getUserId(),
                    foodName,
                    category,
                    quantity,
                    location,
                    availableFrom,
                    pickupDeadline
            );
            System.out.println("\n[SUCCESS] Food donation published successfully!");
            System.out.printf("  Donation ID: #%d%n", donation.getDonationId());
            System.out.printf("  Item: %s [%s] | Total Quantity: %d | Remaining: %d%n",
                    donation.getFoodName(), donation.getFoodCategory(), donation.getQuantity(), donation.getRemainingQuantity());
            System.out.printf("  Pickup Location: %s%n", donation.getPickupLocation());
            System.out.printf("  Window: %s to %s%n", donation.getAvailableFrom(), donation.getPickupDeadline());
            System.out.println("  Alerts have been dispatched to registered NGOs and Volunteers.");
        } catch (ValidationException | BusinessRuleException e) {
            System.out.println("\n[ERROR] " + e.getMessage());
        }
    }

    /**
     * Views donations published by the currently selected provider.
     */
    public void viewMyDonations(User actingUser) {
        if (actingUser == null) {
            System.out.println("\n[ERROR] No active user selected. Please select a provider user first.");
            return;
        }
        if (!actingUser.getUserType().isProvider()) {
            System.out.println("\n[ERROR] Active user is not a provider. Only providers have published donations.");
            return;
        }

        System.out.printf("\n--- My Published Donations (Provider: %s #%d) ---%n",
                actingUser.getUserName(), actingUser.getUserId());

        try {
            List<FoodDonation> list = donationService.getDonationsByProvider(actingUser.getUserId());
            if (list.isEmpty()) {
                System.out.println("No donations published yet.");
                return;
            }
            displayDonationTable(list);
        } catch (Exception e) {
            System.out.println("[ERROR] Failed to load donations: " + e.getMessage());
        }
    }

    /**
     * Feature 3 — Search Available Food with optional filters.
     */
    public void searchAvailableFood(Scanner scanner) {
        System.out.println("\n--- Search Available Food Donations ---");
        System.out.println("(Press Enter on any filter to skip it)");

        String category = ConsoleInput.readString(scanner, "Filter by Category (optional): ", false);
        Integer minQty = ConsoleInput.readOptionalInt(scanner, "Minimum Quantity Required (optional): ", 1);
        String location = ConsoleInput.readString(scanner, "Filter by Location / City (optional): ", false);

        try {
            List<FoodDonation> results = donationService.searchAvailable(category, minQty, location);
            if (results.isEmpty()) {
                System.out.println("\nNo available food donations matched your search criteria.");
                return;
            }
            System.out.printf("\nFound %d available donation(s):%n", results.size());
            displayDonationTable(results);
        } catch (Exception e) {
            System.out.println("[ERROR] Search failed: " + e.getMessage());
        }
    }

    /**
     * Feature 6 — Smart Food Matching.
     */
    public void findSmartMatches(Scanner scanner, User actingUser) {
        System.out.println("\n--- Smart Food Matching Algorithm ---");
        if (actingUser != null && actingUser.getUserType().isRequester()) {
            System.out.printf("Matching recommendations for: %s (%s, Location: %s)%n",
                    actingUser.getUserName(), actingUser.getUserType(), actingUser.getLocation());
        }

        String defaultLoc = actingUser != null ? actingUser.getLocation() : null;
        String promptLoc = defaultLoc != null ?
                "Desired Pickup Location / Area (Press Enter to use your location: '" + defaultLoc + "'): " :
                "Desired Pickup Location / Area (optional): ";

        String desiredLoc = ConsoleInput.readString(scanner, promptLoc, false);
        if ((desiredLoc == null || desiredLoc.isEmpty()) && defaultLoc != null) {
            desiredLoc = defaultLoc;
        }

        Integer targetQty = ConsoleInput.readOptionalInt(scanner, "Required Portions / Quantity (optional): ", 1);
        Integer maxHours = ConsoleInput.readOptionalInt(scanner, "Maximum hours until deadline (e.g. 6) (optional): ", 1);

        Double maxHoursDouble = maxHours != null ? maxHours.doubleValue() : null;

        try {
            List<FoodMatchingService.MatchResult> matches = matchingService.findMatches(desiredLoc, targetQty, maxHoursDouble);
            if (matches.isEmpty()) {
                System.out.println("\nNo eligible donations found for smart matching right now.");
                return;
            }

            System.out.printf("\n=== Top Ranked Food Matches (%d found) ===%n", matches.size());
            int rank = 1;
            for (FoodMatchingService.MatchResult res : matches) {
                FoodDonation d = res.getDonation();
                System.out.printf("\n#%d | Match Score: %d/100 | Donation #%d: '%s' [%s]%n",
                        rank++, res.getScore(), d.getDonationId(), d.getFoodName(), d.getFoodCategory());
                System.out.printf("    Remaining Available: %d / %d portions | Status: %s%n",
                        d.getRemainingQuantity(), d.getQuantity(), d.getStatus());
                System.out.printf("    Pickup Location: %s | Provider: %s%n",
                        d.getPickupLocation(), d.getProviderName() != null ? d.getProviderName() : "Provider #" + d.getProviderId());
                System.out.printf("    Deadline: %s (%.1f hours remaining)%n",
                        d.getPickupDeadline(), res.getHoursRemaining());
                System.out.printf("    Evaluation: %s%n", res.getMatchReason());
                System.out.println("    " + "-".repeat(70));
            }
        } catch (Exception e) {
            System.out.println("[ERROR] Matching failed: " + e.getMessage());
        }
    }

    private void displayDonationTable(List<FoodDonation> list) {
        System.out.printf("%-5s | %-20s | %-14s | %-6s | %-9s | %-24s | %-16s | %-10s%n",
                "ID", "Food Name", "Category", "Total", "Remaining", "Pickup Location", "Deadline", "Status");
        System.out.println("-".repeat(115));
        for (FoodDonation d : list) {
            System.out.printf("%-5d | %-20s | %-14s | %-6d | %-9d | %-24s | %-16s | %-10s%n",
                    d.getDonationId(),
                    truncate(d.getFoodName(), 20),
                    truncate(d.getFoodCategory(), 14),
                    d.getQuantity(),
                    d.getRemainingQuantity(),
                    truncate(d.getPickupLocation(), 24),
                    d.getPickupDeadline().toString().replace('T', ' '),
                    d.getStatus());
        }
    }

    private String truncate(String val, int max) {
        if (val == null) return "N/A";
        return val.length() <= max ? val : val.substring(0, max - 3) + "...";
    }
}
