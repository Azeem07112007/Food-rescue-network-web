package com.foodrescue.repository;

import com.foodrescue.config.DBConnection;
import com.foodrescue.model.PickupRequest;
import com.foodrescue.model.enums.RequestStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository for 'pickup_requests' table operations using JDBC PreparedStatement.
 * Mapped to the existing MySQL schema.
 */
public class PickupRequestRepository {

    public PickupRequest save(PickupRequest request) throws SQLException {
        String sql = "INSERT INTO pickup_requests (donation_id, requester_id, requested_quantity, request_time, status) " +
                "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, request.getDonationId());
            stmt.setInt(2, request.getRequesterId());
            stmt.setInt(3, request.getRequestedQuantity());
            stmt.setTimestamp(4, Timestamp.valueOf(request.getRequestTime()));
            stmt.setString(5, request.getStatus().name());

            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating pickup request failed, no rows affected.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    request.setRequestId(generatedKeys.getInt(1));
                } else {
                    throw new SQLException("Creating pickup request failed, no ID obtained.");
                }
            }
            return request;
        }
    }

    public Optional<PickupRequest> findById(int requestId) throws SQLException {
        String sql = "SELECT r.*, d.food_name, d.provider_id, u.user_name AS requester_name FROM pickup_requests r " +
                "JOIN food_donations d ON r.donation_id = d.donation_id " +
                "JOIN users u ON r.requester_id = u.user_id " +
                "WHERE r.request_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, requestId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToRequest(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Retrieves pickup request with row-level lock (FOR UPDATE) inside an active transaction.
     */
    public Optional<PickupRequest> findByIdForUpdate(Connection conn, int requestId) throws SQLException {
        String sql = "SELECT r.*, d.food_name, d.provider_id, u.user_name AS requester_name FROM pickup_requests r " +
                "JOIN food_donations d ON r.donation_id = d.donation_id " +
                "JOIN users u ON r.requester_id = u.user_id " +
                "WHERE r.request_id = ? FOR UPDATE";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, requestId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToRequest(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<PickupRequest> findByProviderId(int providerId) throws SQLException {
        String sql = "SELECT r.*, d.food_name, d.provider_id, u.user_name AS requester_name FROM pickup_requests r " +
                "JOIN food_donations d ON r.donation_id = d.donation_id " +
                "JOIN users u ON r.requester_id = u.user_id " +
                "WHERE d.provider_id = ? ORDER BY r.request_id DESC";
        List<PickupRequest> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, providerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToRequest(rs));
                }
            }
        }
        return list;
    }

    public List<PickupRequest> findByRequesterId(int requesterId) throws SQLException {
        String sql = "SELECT r.*, d.food_name, d.provider_id, u.user_name AS requester_name FROM pickup_requests r " +
                "JOIN food_donations d ON r.donation_id = d.donation_id " +
                "JOIN users u ON r.requester_id = u.user_id " +
                "WHERE r.requester_id = ? ORDER BY r.request_id DESC";
        List<PickupRequest> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, requesterId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToRequest(rs));
                }
            }
        }
        return list;
    }

    public List<PickupRequest> findByDonationId(int donationId) throws SQLException {
        String sql = "SELECT r.*, d.food_name, d.provider_id, u.user_name AS requester_name FROM pickup_requests r " +
                "JOIN food_donations d ON r.donation_id = d.donation_id " +
                "JOIN users u ON r.requester_id = u.user_id " +
                "WHERE r.donation_id = ? ORDER BY r.request_id DESC";
        List<PickupRequest> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, donationId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToRequest(rs));
                }
            }
        }
        return list;
    }

    public void updateStatus(Connection conn, int requestId, RequestStatus status) throws SQLException {
        String sql = "UPDATE pickup_requests SET status = ? WHERE request_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status.name());
            stmt.setInt(2, requestId);
            stmt.executeUpdate();
        }
    }

    public void updateStatus(int requestId, RequestStatus status) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            updateStatus(conn, requestId, status);
        }
    }

    public int sumApprovedQuantityForDonation(Connection conn, int donationId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(requested_quantity), 0) FROM pickup_requests " +
                "WHERE donation_id = ? AND status = 'APPROVED'";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, donationId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public boolean hasOtherActiveApprovedRequests(Connection conn, int donationId, int excludeRequestId) throws SQLException {
        String sql = "SELECT 1 FROM pickup_requests WHERE donation_id = ? AND status = 'APPROVED' AND request_id != ? LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, donationId);
            stmt.setInt(2, excludeRequestId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private PickupRequest mapResultSetToRequest(ResultSet rs) throws SQLException {
        PickupRequest req = new PickupRequest();
        req.setRequestId(rs.getInt("request_id"));
        req.setDonationId(rs.getInt("donation_id"));
        req.setRequesterId(rs.getInt("requester_id"));
        req.setRequestedQuantity(rs.getInt("requested_quantity"));
        req.setRequestTime(rs.getTimestamp("request_time").toLocalDateTime());
        req.setStatus(RequestStatus.fromString(rs.getString("status")));

        try {
            req.setFoodName(rs.getString("food_name"));
            req.setProviderId(rs.getInt("provider_id"));
            req.setRequesterName(rs.getString("requester_name"));
        } catch (SQLException ignored) {}
        return req;
    }
}
