-- ============================================================================
-- FOOD RESCUE NETWORK - CORE DATABASE SCHEMA
-- Target Database: MySQL 8.x / 9.x (InnoDB Engine)
-- Fully aligned with the active 'food_rescue' production schema.
-- ============================================================================

CREATE DATABASE IF NOT EXISTS food_rescue
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE food_rescue;

-- ----------------------------------------------------------------------------
-- 1. USERS TABLE
-- Stores donor providers and requesters with role restriction and location.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    user_name VARCHAR(100) NOT NULL,
    email VARCHAR(120) NOT NULL,
    contact_info VARCHAR(30) NOT NULL,
    user_type ENUM('RESTAURANT', 'HOSTEL', 'CANTEEN', 'EVENT_ORGANIZER', 'NGO', 'VOLUNTEER') NOT NULL,
    location VARCHAR(100) NOT NULL,
    UNIQUE KEY email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 2. FOOD_DONATIONS TABLE
-- Stores surplus food listings using initial 'quantity' and current 'remaining_quantity'.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS food_donations (
    donation_id INT AUTO_INCREMENT PRIMARY KEY,
    provider_id INT NOT NULL,
    food_name VARCHAR(100) NOT NULL,
    food_category VARCHAR(50) NOT NULL,
    quantity INT NOT NULL,
    remaining_quantity INT NOT NULL,
    pickup_location VARCHAR(150) NOT NULL,
    available_from DATETIME NOT NULL,
    pickup_deadline DATETIME NOT NULL,
    status ENUM('AVAILABLE', 'RESERVED', 'COLLECTED', 'EXPIRED', 'CANCELLED') NOT NULL DEFAULT 'AVAILABLE',
    KEY idx_don_status_deadline (status, pickup_deadline),
    KEY idx_donations_status_deadline (status, pickup_deadline),
    KEY idx_donations_location (pickup_location),
    KEY idx_donations_provider (provider_id),
    CONSTRAINT fk_don_provider FOREIGN KEY (provider_id) REFERENCES users (user_id),
    CONSTRAINT food_donations_chk_1 CHECK (quantity > 0),
    CONSTRAINT food_donations_chk_2 CHECK (remaining_quantity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 3. PICKUP_REQUESTS TABLE
-- Stores food pickup requests placed by NGOs and Volunteers against donations.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pickup_requests (
    request_id INT AUTO_INCREMENT PRIMARY KEY,
    donation_id INT NOT NULL,
    requester_id INT NOT NULL,
    requested_quantity INT NOT NULL,
    request_time DATETIME NOT NULL,
    status ENUM('PENDING', 'APPROVED', 'REJECTED', 'COMPLETED', 'CANCELLED') NOT NULL DEFAULT 'PENDING',
    KEY idx_requests_donation_status (donation_id, status),
    KEY idx_requests_requester (requester_id),
    CONSTRAINT fk_req_donation FOREIGN KEY (donation_id) REFERENCES food_donations (donation_id),
    CONSTRAINT fk_req_requester FOREIGN KEY (requester_id) REFERENCES users (user_id),
    CONSTRAINT pickup_requests_chk_1 CHECK (requested_quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------------------------------------------------------
-- 4. ALERTS TABLE
-- Stores notifications for new food, approaching deadlines, and request updates.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS alerts (
    alert_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    donation_id INT NOT NULL,
    alert_type ENUM('NEW_FOOD', 'DEADLINE', 'INFO') NOT NULL,
    message VARCHAR(255) NOT NULL,
    created_time DATETIME NOT NULL,
    is_read TINYINT(1) NOT NULL DEFAULT '0',
    KEY fk_alert_donation (donation_id),
    KEY idx_alert_user (user_id, is_read),
    CONSTRAINT fk_alert_donation FOREIGN KEY (donation_id) REFERENCES food_donations (donation_id),
    CONSTRAINT fk_alert_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
