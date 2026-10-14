package com.foodrescue.service;

import com.foodrescue.exception.BusinessRuleException;
import com.foodrescue.exception.NotFoundException;
import com.foodrescue.model.User;
import com.foodrescue.model.enums.UserType;
import com.foodrescue.repository.UserRepository;
import com.foodrescue.util.InputValidator;
import java.sql.SQLException;
import java.util.List;

/**
 * Service handling user registration, role validation, and identity lookups.
 * Fully compatible with existing 'users' table schema.
 */
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserService() {
        this(new UserRepository());
    }

    /**
     * Feature 1 — User Registration
     * Validates fields, normalizes email, ensures email uniqueness, and persists user.
     */
    public User registerUser(String userName, String email, String contactInfo, UserType userType, String location) {
        InputValidator.validateNonBlank(userName, "User name");
        InputValidator.validateEmail(email);
        InputValidator.validateNonBlank(contactInfo, "Contact information");
        InputValidator.validateNonBlank(location, "Location / City");
        if (userType == null) {
            throw new BusinessRuleException("User role must be specified.");
        }

        String normalizedEmail = InputValidator.normalizeEmail(email);

        try {
            if (userRepository.existsByEmail(normalizedEmail)) {
                throw new BusinessRuleException("Registration failed: A user with email '" + normalizedEmail + "' already exists.");
            }

            User user = new User(userName.trim(), normalizedEmail, contactInfo.trim(), userType, location.trim());
            return userRepository.save(user);
        } catch (SQLException e) {
            throw new BusinessRuleException("Database error during user registration: " + e.getMessage(), e);
        }
    }

    // Overload for backward compatibility
    public User registerUser(String userName, String email, String contactInfo, UserType userType) {
        return registerUser(userName, email, contactInfo, userType, "Coimbatore");
    }

    public User getUserById(int userId) {
        try {
            return userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("User not found with ID: " + userId));
        } catch (SQLException e) {
            throw new BusinessRuleException("Database error retrieving user: " + e.getMessage(), e);
        }
    }

    public User getUserByEmail(String email) {
        String normalized = InputValidator.normalizeEmail(email);
        try {
            return userRepository.findByEmail(normalized)
                    .orElseThrow(() -> new NotFoundException("User not found with email: " + normalized));
        } catch (SQLException e) {
            throw new BusinessRuleException("Database error retrieving user by email: " + e.getMessage(), e);
        }
    }

    public List<User> getAllUsers() {
        try {
            return userRepository.findAll();
        } catch (SQLException e) {
            throw new BusinessRuleException("Database error listing users: " + e.getMessage(), e);
        }
    }

    public List<User> getRequesters() {
        try {
            return userRepository.findRequesters();
        } catch (SQLException e) {
            throw new BusinessRuleException("Database error listing requesters: " + e.getMessage(), e);
        }
    }
}
