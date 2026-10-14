package com.foodrescue.repository;

import com.foodrescue.config.DBConnection;
import com.foodrescue.model.FoodAlert;
import com.foodrescue.model.enums.AlertType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository for 'alerts' table operations.
 * Mapped to the existing MySQL schema: (alert_id, user_id, donation_id, alert_type, message, created_time, is_read).
 * Supports deduplication checking and enum mapping ('NEW_FOOD', 'DEADLINE', 'INFO').
 */
public class FoodAlertRepository {

    public FoodAlert save(FoodAlert alert) throws SQLException {
        String sql = "INSERT INTO alerts (user_id, donation_id, alert_type, message, created_time, is_read) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, alert.getUserId());
            stmt.setInt(2, alert.getDonationId() != null ? alert.getDonationId() : 1);
            stmt.setString(3, mapToDbAlertType(alert.getAlertType()));

            // Ensure message fits inside varchar(255)
            String msg = alert.getMessage();
            if (msg != null && msg.length() > 250) {
                msg = msg.substring(0, 247) + "...";
            }
            stmt.setString(4, msg);
            stmt.setTimestamp(5, Timestamp.valueOf(alert.getCreatedAt()));
            stmt.setBoolean(6, alert.isRead());

            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Creating food alert failed, no rows affected.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    alert.setAlertId(generatedKeys.getInt(1));
                }
            }
            return alert;
        }
    }

    public List<FoodAlert> findByUserId(int userId, boolean unreadOnly) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM alerts WHERE user_id = ? ");
        if (unreadOnly) {
            sql.append("AND is_read = 0 ");
        }
        sql.append("ORDER BY created_time DESC");

        List<FoodAlert> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToAlert(rs));
                }
            }
        }
        return list;
    }

    public boolean markAsRead(int alertId, int userId) throws SQLException {
        String sql = "UPDATE alerts SET is_read = 1 WHERE alert_id = ? AND user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, alertId);
            stmt.setInt(2, userId);
            return stmt.executeUpdate() > 0;
        }
    }

    public int markAllAsRead(int userId) throws SQLException {
        String sql = "UPDATE alerts SET is_read = 1 WHERE user_id = ? AND is_read = 0";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            return stmt.executeUpdate();
        }
    }

    /**
     * Deduplication query: checks if an alert of the same type was already sent to the user for this donation
     * within the specified hour threshold.
     */
    public boolean existsRecentAlert(int userId, Integer donationId, AlertType alertType, int hoursThreshold) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT 1 FROM alerts WHERE user_id = ? AND alert_type = ? AND created_time >= ? "
        );
        if (donationId != null) {
            sql.append("AND donation_id = ?");
        }

        LocalDateTime cutoff = LocalDateTime.now().minusHours(hoursThreshold);
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            stmt.setInt(1, userId);
            stmt.setString(2, mapToDbAlertType(alertType));
            stmt.setTimestamp(3, Timestamp.valueOf(cutoff));
            if (donationId != null) {
                stmt.setInt(4, donationId);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private String mapToDbAlertType(AlertType alertType) {
        if (alertType == null) {
            return "INFO";
        }
        return switch (alertType) {
            case NEW_DONATION -> "NEW_FOOD";
            case DEADLINE_APPROACHING, DONATION_EXPIRED -> "DEADLINE";
            default -> "INFO";
        };
    }

    private FoodAlert mapResultSetToAlert(ResultSet rs) throws SQLException {
        FoodAlert alert = new FoodAlert();
        alert.setAlertId(rs.getInt("alert_id"));
        alert.setUserId(rs.getInt("user_id"));
        alert.setDonationId(rs.getInt("donation_id"));

        String rawType = rs.getString("alert_type");
        if ("NEW_FOOD".equalsIgnoreCase(rawType)) {
            alert.setAlertType(AlertType.NEW_DONATION);
        } else if ("DEADLINE".equalsIgnoreCase(rawType)) {
            alert.setAlertType(AlertType.DEADLINE_APPROACHING);
        } else {
            alert.setAlertType(AlertType.REQUEST_APPROVED);
        }

        alert.setMessage(rs.getString("message"));
        alert.setRead(rs.getBoolean("is_read"));
        Timestamp ts = rs.getTimestamp("created_time");
        if (ts != null) {
            alert.setCreatedAt(ts.toLocalDateTime());
        }
        return alert;
    }
}
