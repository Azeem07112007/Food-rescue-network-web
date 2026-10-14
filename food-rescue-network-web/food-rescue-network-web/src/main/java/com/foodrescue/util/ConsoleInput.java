package com.foodrescue.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Scanner;

/**
 * Utility class providing safe and user-friendly console reading routines.
 */
public final class ConsoleInput {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private ConsoleInput() {
    }

    public static String readString(Scanner scanner, String prompt, boolean required) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine();
            if (input != null) {
                input = input.trim();
            }
            if (!required || (input != null && !input.isEmpty())) {
                return input;
            }
            System.out.println("  [Error] This field is required. Please enter a value.");
        }
    }

    public static int readInt(Scanner scanner, String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine();
            try {
                int val = Integer.parseInt(line.trim());
                if (val >= min && val <= max) {
                    return val;
                }
                System.out.printf("  [Error] Value must be between %d and %d.%n", min, max);
            } catch (NumberFormatException e) {
                System.out.println("  [Error] Please enter a valid whole number.");
            }
        }
    }

    public static Integer readOptionalInt(Scanner scanner, String prompt, int min) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine();
            if (line == null || line.trim().isEmpty()) {
                return null;
            }
            try {
                int val = Integer.parseInt(line.trim());
                if (val >= min) {
                    return val;
                }
                System.out.printf("  [Error] Value must be at least %d.%n", min);
            } catch (NumberFormatException e) {
                System.out.println("  [Error] Please enter a valid whole number or leave blank to skip.");
            }
        }
    }

    public static LocalDateTime readDateTime(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt + " (Format: yyyy-MM-dd HH:mm): ");
            String line = scanner.nextLine();
            if (line == null || line.trim().isEmpty()) {
                System.out.println("  [Error] Date and time is required.");
                continue;
            }
            try {
                return LocalDateTime.parse(line.trim(), DATE_TIME_FORMATTER);
            } catch (DateTimeParseException e) {
                System.out.println("  [Error] Invalid format. Example: 2026-10-15 18:30");
            }
        }
    }

    public static LocalDateTime readOptionalDateTime(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt + " (Format: yyyy-MM-dd HH:mm, or press Enter to skip): ");
            String line = scanner.nextLine();
            if (line == null || line.trim().isEmpty()) {
                return null;
            }
            try {
                return LocalDateTime.parse(line.trim(), DATE_TIME_FORMATTER);
            } catch (DateTimeParseException e) {
                System.out.println("  [Error] Invalid format. Example: 2026-10-15 18:30 or leave blank.");
            }
        }
    }

    public static boolean readYesNo(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt + " (y/n): ");
            String line = scanner.nextLine();
            if (line != null) {
                line = line.trim().toLowerCase();
                if (line.equals("y") || line.equals("yes")) {
                    return true;
                }
                if (line.equals("n") || line.equals("no")) {
                    return false;
                }
            }
            System.out.println("  [Error] Please enter 'y' for yes or 'n' for no.");
        }
    }
}
