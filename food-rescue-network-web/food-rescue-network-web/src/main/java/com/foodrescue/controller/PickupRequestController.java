package com.foodrescue.controller;

import com.foodrescue.exception.BusinessRuleException;
import com.foodrescue.exception.ValidationException;
import com.foodrescue.model.FoodAlert;
import com.foodrescue.model.PickupRequest;
import com.foodrescue.model.User;
import com.foodrescue.service.FoodAlertService;
import com.foodrescue.service.PickupRequestService;
import com.foodrescue.util.ConsoleInput;
import java.util.List;
import java.util.Scanner;

/**
 * Controller handling pickup request creation, approval, rejection, cancellation, completion, and alert views.
 */
public class PickupRequestController {

    private final PickupRequestService requestService;
    private final FoodAlertService alertService;

    public PickupRequestController(PickupRequestService requestService, FoodAlertService alertService) {
        this.requestService = requestService;
        this.alertService = alertService;
    }

    public PickupRequestController() {
        this(new PickupRequestService(), new FoodAlertService());
    }

    /**
     * Feature 4 — Request Food Pickup.
     */
    public void createPickupRequest(Scanner scanner, User actingUser) {
        if (actingUser == null) {
            System.out.println("\n[ERROR] No active user selected. Please select an NGO or Volunteer user first (Menu Option 3).");
            return;
        }

        if (!actingUser.getUserType().isRequester()) {
            System.out.printf("\n[ERROR] Role restriction: Current user '%s' has role '%s'. Only NGOs and Volunteers can request pickups.%n",
                    actingUser.getUserName(), actingUser.getUserType());
            return;
        }

        System.out.println("\n--- Create Food Pickup Request ---");
        System.out.printf("Requesting as: %s (ID: %d, Role: %s)%n", actingUser.getUserName(), actingUser.getUserId(), actingUser.getUserType());

        int donationId = ConsoleInput.readInt(scanner, "Enter Donation ID to request: ", 1, Integer.MAX_VALUE);
        int quantity = ConsoleInput.readInt(scanner, "Enter Requested Quantity: ", 1, 100000);
        String remarks = ConsoleInput.readString(scanner, "Enter Pickup Notes / Timing details (optional): ", false);

        try {
            PickupRequest req = requestService.createPickupRequest(donationId, actingUser.getUserId(), quantity, remarks);
            System.out.println("\n[SUCCESS] Pickup request submitted successfully!");
            System.out.printf("  Request ID: #%d | Status: %s%n", req.getRequestId(), req.getStatus());
            System.out.printf("  Requested Portions: %d for Donation #%d%n", req.getRequestedQuantity(), req.getDonationId());
            System.out.println("  The provider has been notified to review and approve your request.");
        } catch (ValidationException | BusinessRuleException e) {
            System.out.println("\n[ERROR] " + e.getMessage());
        }
    }

    /**
     * Views pickup requests (either incoming for providers or outgoing for requesters).
     */
    public void viewPickupRequests(User actingUser) {
        if (actingUser == null) {
            System.out.println("\n[ERROR] No active user selected. Please select a user session first.");
            return;
        }

        System.out.printf("\n--- Pickup Requests for %s (#%d, %s) ---%n",
                actingUser.getUserName(), actingUser.getUserId(), actingUser.getUserType());

        try {
            List<PickupRequest> requests;
            if (actingUser.getUserType().isProvider()) {
                System.out.println("(Showing incoming requests for your food donations)");
                requests = requestService.getRequestsForProvider(actingUser.getUserId());
            } else {
                System.out.println("(Showing your submitted requests)");
                requests = requestService.getRequestsForRequester(actingUser.getUserId());
            }

            if (requests.isEmpty()) {
                System.out.println("No pickup requests found.");
                return;
            }

            displayRequestTable(requests);
        } catch (Exception e) {
            System.out.println("[ERROR] Failed to load requests: " + e.getMessage());
        }
    }

    /**
     * Feature 5 — Approve Pickup Request.
     */
    public void approvePickupRequest(Scanner scanner, User actingUser) {
        if (actingUser == null || !actingUser.getUserType().isProvider()) {
            System.out.println("\n[ERROR] Only the food provider who owns the donation can approve requests.");
            return;
        }

        System.out.println("\n--- Approve Food Pickup Request ---");
        int requestId = ConsoleInput.readInt(scanner, "Enter Request ID to approve: ", 1, Integer.MAX_VALUE);

        try {
            requestService.approveRequest(requestId, actingUser.getUserId());
            System.out.printf("\n[SUCCESS] Request #%d approved! Food portions reserved successfully in transaction.%n", requestId);
        } catch (Exception e) {
            System.out.println("\n[ERROR] " + e.getMessage());
        }
    }

    /**
     * Feature 5 — Reject Pickup Request.
     */
    public void rejectPickupRequest(Scanner scanner, User actingUser) {
        if (actingUser == null || !actingUser.getUserType().isProvider()) {
            System.out.println("\n[ERROR] Only the food provider who owns the donation can reject requests.");
            return;
        }

        System.out.println("\n--- Reject Food Pickup Request ---");
        int requestId = ConsoleInput.readInt(scanner, "Enter Request ID to reject: ", 1, Integer.MAX_VALUE);
        String reason = ConsoleInput.readString(scanner, "Enter Rejection Reason: ", false);

        try {
            requestService.rejectRequest(requestId, actingUser.getUserId(), reason);
            System.out.printf("\n[SUCCESS] Request #%d has been rejected.%n", requestId);
        } catch (Exception e) {
            System.out.println("\n[ERROR] " + e.getMessage());
        }
    }

    /**
     * Feature 8 — Cancel Pickup Request.
     */
    public void cancelPickupRequest(Scanner scanner, User actingUser) {
        if (actingUser == null || !actingUser.getUserType().isRequester()) {
            System.out.println("\n[ERROR] Only the requester (NGO/Volunteer) who created the request can cancel it.");
            return;
        }

        System.out.println("\n--- Cancel Food Pickup Request ---");
        int requestId = ConsoleInput.readInt(scanner, "Enter Request ID to cancel: ", 1, Integer.MAX_VALUE);

        try {
            requestService.cancelRequest(requestId, actingUser.getUserId());
            System.out.printf("\n[SUCCESS] Request #%d has been cancelled. Any reserved quantities were restored atomically.%n", requestId);
        } catch (Exception e) {
            System.out.println("\n[ERROR] " + e.getMessage());
        }
    }

    /**
     * Feature 8 — Mark Pickup as Completed.
     */
    public void completePickup(Scanner scanner, User actingUser) {
        if (actingUser == null || !actingUser.getUserType().isProvider()) {
            System.out.println("\n[ERROR] Only the provider who owns the donation can mark a pickup as completed.");
            return;
        }

        System.out.println("\n--- Complete Food Pickup ---");
        int requestId = ConsoleInput.readInt(scanner, "Enter Request ID to mark as completed: ", 1, Integer.MAX_VALUE);

        try {
            requestService.completePickup(requestId, actingUser.getUserId());
            System.out.printf("\n[SUCCESS] Pickup #%d marked as COMPLETED! Food rescued and recorded successfully.%n", requestId);
        } catch (Exception e) {
            System.out.println("\n[ERROR] " + e.getMessage());
        }
    }

    /**
     * Feature 7 — View Food Alerts.
     */
    public void viewFoodAlerts(Scanner scanner, User actingUser) {
        if (actingUser == null) {
            System.out.println("\n[ERROR] No active user selected. Please select a user session first.");
            return;
        }

        System.out.printf("\n--- Food Alerts for %s (#%d) ---%n", actingUser.getUserName(), actingUser.getUserId());
        try {
            List<FoodAlert> alerts = alertService.getAlertsForUser(actingUser.getUserId(), false);
            if (alerts.isEmpty()) {
                System.out.println("You have no alerts at this time.");
                return;
            }

            for (FoodAlert alert : alerts) {
                System.out.printf("[%s] #%d | %s | %s%n     %s%n",
                        alert.isRead() ? "READ" : "UNREAD",
                        alert.getAlertId(),
                        alert.getAlertType(),
                        alert.getCreatedAt().toString().replace('T', ' '),
                        alert.getMessage());
            }

            System.out.println("\nOptions: (1) Mark alert as read  (2) Mark all as read  (3) Back");
            int choice = ConsoleInput.readInt(scanner, "Select option (1-3): ", 1, 3);
            if (choice == 1) {
                int alertId = ConsoleInput.readInt(scanner, "Enter Alert ID: ", 1, Integer.MAX_VALUE);
                alertService.markAsRead(alertId, actingUser.getUserId());
                System.out.println("[SUCCESS] Alert marked as read.");
            } else if (choice == 2) {
                int count = alertService.markAllAsRead(actingUser.getUserId());
                System.out.printf("[SUCCESS] %d unread alert(s) marked as read.%n", count);
            }
        } catch (Exception e) {
            System.out.println("[ERROR] Failed to load alerts: " + e.getMessage());
        }
    }

    private void displayRequestTable(List<PickupRequest> list) {
        System.out.printf("%-6s | %-12s | %-8s | %-22s | %-20s | %-6s | %-16s%n",
                "Req ID", "Status", "Donation", "Item", "Requester", "Qty", "Request Time");
        System.out.println("-".repeat(105));
        for (PickupRequest r : list) {
            System.out.printf("%-6d | %-12s | %-8d | %-22s | %-20s | %-6d | %-16s%n",
                    r.getRequestId(),
                    r.getStatus(),
                    r.getDonationId(),
                    truncate(r.getFoodName(), 22),
                    truncate(r.getRequesterName(), 20),
                    r.getRequestedQuantity(),
                    r.getRequestTime().toString().replace('T', ' '));
        }
    }

    private String truncate(String val, int max) {
        if (val == null) return "N/A";
        return val.length() <= max ? val : val.substring(0, max - 3) + "...";
    }
}
