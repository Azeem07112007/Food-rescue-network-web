package com.foodrescue;

import com.foodrescue.config.DBConnection;
import com.foodrescue.controller.DonationController;
import com.foodrescue.controller.PickupRequestController;
import com.foodrescue.controller.UserController;
import com.foodrescue.model.User;
import com.foodrescue.service.DeadlineService;
import com.foodrescue.util.ConsoleInput;
import java.util.Scanner;

/**
 * Main entry point for the Food Rescue Network Console Application.
 * Presents a robust 16-option interactive menu, manages acting session state,
 * runs startup deadline audits, and handles runtime errors gracefully.
 */
public class Main {

    private final UserController userController;
    private final DonationController donationController;
    private final PickupRequestController requestController;
    private final DeadlineService deadlineService;
    private User actingUser;

    public Main() {
        this.userController = new UserController();
        this.donationController = new DonationController();
        this.requestController = new PickupRequestController();
        this.deadlineService = new DeadlineService();
    }

    public static void main(String[] args) {
        Main app = new Main();
        app.run();
    }

    public void run() {
        printBanner();

        // 1. Initial Connection Test
        DBConnection.ConnectionTestResult testResult = DBConnection.testConnection();
        if (testResult.isSuccess()) {
            System.out.println("[DB STATUS] " + testResult.getMessage());
            System.out.println("[STARTUP] Running initial deadline checks and automated status transitions...");
            deadlineService.runAllDeadlineChecks();
        } else {
            System.out.println("[DB WARNING] Could not connect to MySQL server.");
            System.out.println("  Detail: " + testResult.getMessage());
            System.out.println("  Configured URL:  " + DBConnection.getDbUrl());
            System.out.println("  Configured User: " + DBConnection.getDbUser());
            System.out.println("  Please check that the MySQL service is running and credentials in .env are correct.");
            System.out.println("  (You can still view the menu, but database operations will require an active connection.)\n");
        }

        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        while (running) {
            try {
                displayMenu();
                int choice = ConsoleInput.readInt(scanner, "\nEnter your choice (1-16): ", 1, 16);
                switch (choice) {
                    case 1:
                        User registered = userController.registerUser(scanner);
                        if (registered != null && actingUser == null) {
                            actingUser = registered;
                            System.out.printf("[AUTO-LOGIN] Switched acting session to newly registered user: %s (#%d)%n",
                                    actingUser.getUserName(), actingUser.getUserId());
                        }
                        break;
                    case 2:
                        userController.listAllUsers();
                        break;
                    case 3:
                        User selected = userController.selectActingUser(scanner);
                        if (selected != null) {
                            actingUser = selected;
                        }
                        break;
                    case 4:
                        donationController.publishDonation(scanner, actingUser);
                        break;
                    case 5:
                        donationController.viewMyDonations(actingUser);
                        break;
                    case 6:
                        donationController.searchAvailableFood(scanner);
                        break;
                    case 7:
                        requestController.createPickupRequest(scanner, actingUser);
                        break;
                    case 8:
                        requestController.viewPickupRequests(actingUser);
                        break;
                    case 9:
                        requestController.approvePickupRequest(scanner, actingUser);
                        break;
                    case 10:
                        requestController.rejectPickupRequest(scanner, actingUser);
                        break;
                    case 11:
                        donationController.findSmartMatches(scanner, actingUser);
                        break;
                    case 12:
                        requestController.viewFoodAlerts(scanner, actingUser);
                        break;
                    case 13:
                        requestController.completePickup(scanner, actingUser);
                        break;
                    case 14:
                        requestController.cancelPickupRequest(scanner, actingUser);
                        break;
                    case 15:
                        System.out.println("\n[MAINTENANCE] Running deadline checks and expiring past-deadline donations...");
                        deadlineService.runAllDeadlineChecks();
                        System.out.println("[MAINTENANCE] Deadline check complete.");
                        break;
                    case 16:
                        running = false;
                        System.out.println("\n========================================================");
                        System.out.println("   Thank you for using Food Rescue Network. Goodbye!    ");
                        System.out.println("========================================================\n");
                        break;
                    default:
                        System.out.println("Invalid choice. Please select 1 through 16.");
                }
            } catch (java.util.NoSuchElementException e) {
                // User interrupted terminal stream or pressed Ctrl+C
                running = false;
                System.out.println("\nSession ended. Goodbye!");
                break;
            } catch (Exception e) {
                System.out.println("\n[UNEXPECTED ERROR] An error occurred: " + e.getMessage());
                // Application loop continues without crashing
            }
        }
    }

    private void displayMenu() {
        System.out.println("\n" + "=".repeat(62));
        System.out.println("             FOOD RESCUE NETWORK - MAIN MENU             ");
        System.out.println("=".repeat(62));
        if (actingUser != null) {
            System.out.printf("  Current Session: %s [ID: %d | Role: %s]%n",
                    actingUser.getUserName(), actingUser.getUserId(), actingUser.getUserType());
        } else {
            System.out.println("  Current Session: NONE (Select Option 3 to login / act as user)");
        }
        System.out.println("-".repeat(62));
        System.out.println("  1.  Register User");
        System.out.println("  2.  List / View Users");
        System.out.println("  3.  Select Acting User for Session");
        System.out.println("  4.  Publish Food Donation (Providers only)");
        System.out.println("  5.  View My Published Donations (Providers only)");
        System.out.println("  6.  Search Available Food (Filters)");
        System.out.println("  7.  Request Food Pickup (NGO / Volunteer only)");
        System.out.println("  8.  View Pickup Requests");
        System.out.println("  9.  Approve Pickup Request (Provider only)");
        System.out.println("  10. Reject Pickup Request (Provider only)");
        System.out.println("  11. Find Smart Food Matches (Recommendations)");
        System.out.println("  12. View Food Alerts (Notifications)");
        System.out.println("  13. Mark Eligible Pickup as Completed (Provider only)");
        System.out.println("  14. Cancel Eligible Pickup Request (Requester only)");
        System.out.println("  15. Refresh / Run Deadline Checks");
        System.out.println("  16. Exit");
        System.out.println("=".repeat(62));
    }

    private void printBanner() {
        System.out.println("\n" + "*".repeat(62));
        System.out.println("*              FOOD RESCUE NETWORK BACKEND                   *");
        System.out.println("*      Surplus Food Redistribution, Management & Alerts      *");
        System.out.println("*".repeat(62));
    }
}
