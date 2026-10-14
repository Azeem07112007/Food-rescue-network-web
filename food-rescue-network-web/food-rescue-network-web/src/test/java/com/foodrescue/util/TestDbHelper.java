package com.foodrescue.util;

import com.foodrescue.config.DBConnection;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Test utility to initialize an in-memory MySQL-compatible H2 database schema for integration tests.
 * Matches the existing MySQL database structure.
 */
public class TestDbHelper {

    public static void setupTestDatabase() {
        String testUrl = "jdbc:h2:mem:test_food_rescue;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        DBConnection.setConfiguration(testUrl, "sa", "", "org.h2.Driver");

        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            // Drop existing tables to ensure a clean state
            stmt.execute("DROP TABLE IF EXISTS alerts");
            stmt.execute("DROP TABLE IF EXISTS pickup_requests");
            stmt.execute("DROP TABLE IF EXISTS food_donations");
            stmt.execute("DROP TABLE IF EXISTS users");

            // Create users (matching existing MySQL schema)
            stmt.execute("CREATE TABLE users (" +
                    "user_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "user_name VARCHAR(100) NOT NULL, " +
                    "email VARCHAR(120) NOT NULL UNIQUE, " +
                    "contact_info VARCHAR(30) NOT NULL, " +
                    "user_type VARCHAR(30) NOT NULL, " +
                    "location VARCHAR(100) NOT NULL" +
                    ")");

            // Create food_donations (matching existing MySQL schema with quantity and remaining_quantity)
            stmt.execute("CREATE TABLE food_donations (" +
                    "donation_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "provider_id INT NOT NULL, " +
                    "food_name VARCHAR(100) NOT NULL, " +
                    "food_category VARCHAR(50) NOT NULL, " +
                    "quantity INT NOT NULL, " +
                    "remaining_quantity INT NOT NULL, " +
                    "pickup_location VARCHAR(150) NOT NULL, " +
                    "available_from TIMESTAMP NOT NULL, " +
                    "pickup_deadline TIMESTAMP NOT NULL, " +
                    "status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE', " +
                    "FOREIGN KEY (provider_id) REFERENCES users(user_id)" +
                    ")");

            // Create pickup_requests (matching existing MySQL schema)
            stmt.execute("CREATE TABLE pickup_requests (" +
                    "request_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "donation_id INT NOT NULL, " +
                    "requester_id INT NOT NULL, " +
                    "requested_quantity INT NOT NULL, " +
                    "request_time TIMESTAMP NOT NULL, " +
                    "status VARCHAR(20) NOT NULL DEFAULT 'PENDING', " +
                    "FOREIGN KEY (donation_id) REFERENCES food_donations(donation_id), " +
                    "FOREIGN KEY (requester_id) REFERENCES users(user_id)" +
                    ")");

            // Create alerts (matching existing MySQL schema)
            stmt.execute("CREATE TABLE alerts (" +
                    "alert_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "user_id INT NOT NULL, " +
                    "donation_id INT NOT NULL, " +
                    "alert_type VARCHAR(20) NOT NULL, " +
                    "message VARCHAR(255) NOT NULL, " +
                    "created_time TIMESTAMP NOT NULL, " +
                    "is_read BOOLEAN NOT NULL DEFAULT FALSE, " +
                    "FOREIGN KEY (user_id) REFERENCES users(user_id), " +
                    "FOREIGN KEY (donation_id) REFERENCES food_donations(donation_id)" +
                    ")");

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize test database", e);
        }
    }
}
