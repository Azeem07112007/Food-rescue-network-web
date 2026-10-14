package com.foodrescue.controller;

import com.foodrescue.exception.BusinessRuleException;
import com.foodrescue.exception.ValidationException;
import com.foodrescue.model.User;
import com.foodrescue.model.enums.UserType;
import com.foodrescue.service.UserService;
import com.foodrescue.util.ConsoleInput;
import java.util.List;
import java.util.Scanner;

/**
 * Controller coordinating user registration, role selection, and user management.
 */
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    public UserController() {
        this(new UserService());
    }

    /**
     * Feature 1 — User Registration flow from console.
     */
    public User registerUser(Scanner scanner) {
        System.out.println("\n--- User Registration ---");
        String name = ConsoleInput.readString(scanner, "Enter Name / Organization: ", true);
        String email = ConsoleInput.readString(scanner, "Enter Email Address: ", true);
        String contact = ConsoleInput.readString(scanner, "Enter Contact Info (Phone/Address): ", true);
        String location = ConsoleInput.readString(scanner, "Enter Location / City (e.g. Coimbatore, Chennai): ", true);

        System.out.println("Select User Type / Role:");
        UserType[] types = UserType.values();
        for (int i = 0; i < types.length; i++) {
            System.out.printf("  %d. %s (%s)%n", i + 1, types[i],
                    types[i].isProvider() ? "Donor / Provider" : "Requester / Beneficiary");
        }
        int typeChoice = ConsoleInput.readInt(scanner, "Select role (1-" + types.length + "): ", 1, types.length);
        UserType selectedType = types[typeChoice - 1];

        try {
            User registered = userService.registerUser(name, email, contact, selectedType, location);
            System.out.println("\n[SUCCESS] User registered successfully!");
            System.out.printf("  Generated User ID: %d%n", registered.getUserId());
            System.out.printf("  Name: %s | Role: %s | Location: %s | Email: %s%n",
                    registered.getUserName(), registered.getUserType(), registered.getLocation(), registered.getEmail());
            return registered;
        } catch (ValidationException | BusinessRuleException e) {
            System.out.println("\n[ERROR] " + e.getMessage());
            return null;
        }
    }

    /**
     * Lists all registered users in the database.
     */
    public void listAllUsers() {
        System.out.println("\n--- Registered Users ---");
        try {
            List<User> users = userService.getAllUsers();
            if (users.isEmpty()) {
                System.out.println("No users found in database. Use option 1 to register a user.");
                return;
            }
            System.out.printf("%-6s | %-24s | %-16s | %-16s | %-28s | %-16s%n",
                    "ID", "Name", "Role", "Location", "Email", "Contact");
            System.out.println("-".repeat(112));
            for (User u : users) {
                System.out.printf("%-6d | %-24s | %-16s | %-16s | %-28s | %-16s%n",
                        u.getUserId(),
                        truncate(u.getUserName(), 24),
                        u.getUserType(),
                        truncate(u.getLocation(), 16),
                        truncate(u.getEmail(), 28),
                        truncate(u.getContactInfo(), 16));
            }
        } catch (Exception e) {
            System.out.println("[ERROR] Failed to list users: " + e.getMessage());
        }
    }

    /**
     * Selects an existing user to act as for the current console session.
     */
    public User selectActingUser(Scanner scanner) {
        System.out.println("\n--- Select Acting User Session ---");
        int userId = ConsoleInput.readInt(scanner, "Enter User ID to login as: ", 1, Integer.MAX_VALUE);
        try {
            User user = userService.getUserById(userId);
            System.out.printf("\n[SESSION ACTIVE] Switched session to: %s (ID: %d, Role: %s, Location: %s)%n",
                    user.getUserName(), user.getUserId(), user.getUserType(), user.getLocation());
            return user;
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
            return null;
        }
    }

    private String truncate(String val, int max) {
        if (val == null) return "N/A";
        return val.length() <= max ? val : val.substring(0, max - 3) + "...";
    }
}
