package com.foodrescue.repository;

import com.foodrescue.config.DBConnection;
import com.foodrescue.model.FoodDonation;
import com.foodrescue.model.enums.DonationStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository for 'food_donations' table operations using JDBC PreparedStatement.
 * Mapped to the existing MySQL schema using 'quantity' and 'remaining_quantity'.
 */
public class DonationRepository {

    public FoodDonation save(FoodDonation donation) throws SQLException {
        String sql = "INSERT INTO food_donations " +
                "(provider_id, food_name, food_category, quantity, remaining_quantity, pickup_location, available_from, pickup_deadline, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, donation.getProviderId());
            stmt.setString(2, donation.getFoodName());
            stmt.setString(3, donation.getFoodCategory());
            stmt.setInt(4, donation.getQuantity());
            stmt.setInt(5, donation.getRemainingQuantity());
            stmt.setString(6, donation.getPickupLocation());
            stmt.setTimestamp(7, Timestamp.valueOf(donation.getAvailableFrom()));
            stmt.setTimestamp(8, Timestamp.valueOf(donation.getPickupDeadline()));
            stmt.setString(9, donation.getStatus().name());

            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating food donation failed, no rows affected.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    donation.setDonationId(generatedKeys.getInt(1));
                } else {
                    throw new SQLException("Creating food donation failed, no ID obtained.");
                }
            }
            return donation;
        }
    }

    public Optional<FoodDonation> findById(int donationId) throws SQLException {
        String sql = "SELECT d.*, u.user_name AS provider_name, u.location AS provider_location FROM food_donations d " +
                "JOIN users u ON d.provider_id = u.user_id WHERE d.donation_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, donationId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToDonation(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Retrieves donation with exclusive row-level lock (FOR UPDATE) inside an active transaction.
     */
    public Optional<FoodDonation> findByIdForUpdate(Connection conn, int donationId) throws SQLException {
        String sql = "SELECT d.*, u.user_name AS provider_name, u.location AS provider_location FROM food_donations d " +
                "JOIN users u ON d.provider_id = u.user_id WHERE d.donation_id = ? FOR UPDATE";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, donationId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToDonation(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<FoodDonation> findByProviderId(int providerId) throws SQLException {
        String sql = "SELECT d.*, u.user_name AS provider_name, u.location AS provider_location FROM food_donations d " +
                "JOIN users u ON d.provider_id = u.user_id WHERE d.provider_id = ? ORDER BY d.donation_id DESC";
        List<FoodDonation> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, providerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToDonation(rs));
                }
            }
        }
        return list;
    }

    /**
     * Searches eligible available food donations using parameterized dynamic filters.
     * Prevents SQL injection and excludes expired, cancelled, and collected donations.
     */
    public List<FoodDonation> searchAvailable(String category, Integer minQuantity, String location,
                                              LocalDateTime now) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT d.*, u.user_name AS provider_name, u.location AS provider_location FROM food_donations d " +
                "JOIN users u ON d.provider_id = u.user_id " +
                "WHERE d.status IN ('AVAILABLE', 'RESERVED') " +
                "AND d.remaining_quantity > 0 " +
                "AND d.pickup_deadline > ? "
        );

        List<Object> params = new ArrayList<>();
        params.add(Timestamp.valueOf(now));

        if (category != null && !category.trim().isEmpty()) {
            sql.append("AND LOWER(d.food_category) LIKE LOWER(?) ");
            params.add("%" + category.trim() + "%");
        }

        if (minQuantity != null && minQuantity > 0) {
            sql.append("AND d.remaining_quantity >= ? ");
            params.add(minQuantity);
        }

        if (location != null && !location.trim().isEmpty()) {
            sql.append("AND LOWER(d.pickup_location) LIKE LOWER(?) ");
            params.add("%" + location.trim() + "%");
        }

        sql.append("ORDER BY d.pickup_deadline ASC");

        List<FoodDonation> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToDonation(rs));
                }
            }
        }
        return list;
    }

    /**
     * Atomically updates remaining_quantity and status within an active transaction.
     */
    public void updateRemainingQuantityAndStatus(Connection conn, int donationId, int remainingQuantity,
                                                DonationStatus status) throws SQLException {
        String sql = "UPDATE food_donations SET remaining_quantity = ?, status = ? WHERE donation_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, remainingQuantity);
            stmt.setString(2, status.name());
            stmt.setInt(3, donationId);
            stmt.executeUpdate();
        }
    }

    public void updateStatus(Connection conn, int donationId, DonationStatus status) throws SQLException {
        String sql = "UPDATE food_donations SET status = ? WHERE donation_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status.name());
            stmt.setInt(2, donationId);
            stmt.executeUpdate();
        }
    }

    public void updateStatus(int donationId, DonationStatus status) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            updateStatus(conn, donationId, status);
        }
    }

    public List<FoodDonation> findExpiredDonations(LocalDateTime now) throws SQLException {
        String sql = "SELECT d.*, u.user_name AS provider_name, u.location AS provider_location FROM food_donations d " +
                "JOIN users u ON d.provider_id = u.user_id " +
                "WHERE d.pickup_deadline <= ? AND d.status IN ('AVAILABLE', 'RESERVED')";
        List<FoodDonation> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(now));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToDonation(rs));
                }
            }
        }
        return list;
    }

    public List<FoodDonation> findApproachingDeadlineDonations(LocalDateTime now, LocalDateTime threshold) throws SQLException {
        String sql = "SELECT d.*, u.user_name AS provider_name, u.location AS provider_location FROM food_donations d " +
                "JOIN users u ON d.provider_id = u.user_id " +
                "WHERE d.pickup_deadline > ? AND d.pickup_deadline <= ? AND d.status IN ('AVAILABLE', 'RESERVED')";
        List<FoodDonation> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(now));
            stmt.setTimestamp(2, Timestamp.valueOf(threshold));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToDonation(rs));
                }
            }
        }
        return list;
    }

    private FoodDonation mapResultSetToDonation(ResultSet rs) throws SQLException {
        FoodDonation d = new FoodDonation();
        d.setDonationId(rs.getInt("donation_id"));
        d.setProviderId(rs.getInt("provider_id"));
        d.setFoodName(rs.getString("food_name"));
        d.setFoodCategory(rs.getString("food_category"));
        d.setQuantity(rs.getInt("quantity"));
        d.setRemainingQuantity(rs.getInt("remaining_quantity"));
        d.setPickupLocation(rs.getString("pickup_location"));
        d.setAvailableFrom(rs.getTimestamp("available_from").toLocalDateTime());
        d.setPickupDeadline(rs.getTimestamp("pickup_deadline").toLocalDateTime());
        d.setStatus(DonationStatus.fromString(rs.getString("status")));

        try {
            d.setProviderName(rs.getString("provider_name"));
        } catch (SQLException ignored) {}

        try {
            d.setProviderLocation(rs.getString("provider_location"));
        } catch (SQLException ignored) {}

        return d;
    }
}
