package com.foodrescue.repository;

import com.foodrescue.config.DBConnection;
import com.foodrescue.model.User;
import com.foodrescue.model.enums.UserType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository for 'users' table operations using JDBC and PreparedStatement.
 * Mapped to the existing MySQL schema.
 */
public class UserRepository {

    public User save(User user) throws SQLException {
        String sql = "INSERT INTO users (user_name, email, contact_info, user_type, location) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, user.getUserName());
            stmt.setString(2, user.getEmail());
            stmt.setString(3, user.getContactInfo());
            stmt.setString(4, user.getUserType().name());
            stmt.setString(5, user.getLocation());

            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating user failed, no rows affected.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    user.setUserId(generatedKeys.getInt(1));
                } else {
                    throw new SQLException("Creating user failed, no ID obtained.");
                }
            }
            return user;
        }
    }

    public Optional<User> findById(int userId) throws SQLException {
        String sql = "SELECT user_id, user_name, email, contact_info, user_type, location FROM users WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToUser(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<User> findByEmail(String email) throws SQLException {
        String sql = "SELECT user_id, user_name, email, contact_info, user_type, location FROM users WHERE LOWER(email) = LOWER(?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToUser(rs));
                }
            }
        }
        return Optional.empty();
    }

    public boolean existsByEmail(String email) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE LOWER(email) = LOWER(?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    public List<User> findAll() throws SQLException {
        String sql = "SELECT user_id, user_name, email, contact_info, user_type, location FROM users ORDER BY user_id ASC";
        List<User> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSetToUser(rs));
            }
        }
        return list;
    }

    public List<User> findRequesters() throws SQLException {
        String sql = "SELECT user_id, user_name, email, contact_info, user_type, location FROM users WHERE user_type IN ('NGO', 'VOLUNTEER') ORDER BY user_id ASC";
        List<User> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSetToUser(rs));
            }
        }
        return list;
    }

    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setUserId(rs.getInt("user_id"));
        user.setUserName(rs.getString("user_name"));
        user.setEmail(rs.getString("email"));
        user.setContactInfo(rs.getString("contact_info"));
        user.setUserType(UserType.fromString(rs.getString("user_type")));
        user.setLocation(rs.getString("location"));
        return user;
    }
}
