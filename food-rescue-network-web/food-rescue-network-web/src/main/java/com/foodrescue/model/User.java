package com.foodrescue.model;

import com.foodrescue.model.enums.UserType;

/**
 * Model entity representing a registered user in the Food Rescue Network.
 * Mapped to the existing 'users' table in MySQL.
 */
public class User {
    private int userId;
    private String userName;
    private String email;
    private String contactInfo;
    private UserType userType;
    private String location;

    public User() {
    }

    public User(int userId, String userName, String email, String contactInfo, UserType userType, String location) {
        this.userId = userId;
        this.userName = userName;
        this.email = email;
        this.contactInfo = contactInfo;
        this.userType = userType;
        this.location = location;
    }

    public User(String userName, String email, String contactInfo, UserType userType, String location) {
        this.userName = userName;
        this.email = email;
        this.contactInfo = contactInfo;
        this.userType = userType;
        this.location = location;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getContactInfo() {
        return contactInfo;
    }

    public void setContactInfo(String contactInfo) {
        this.contactInfo = contactInfo;
    }

    public UserType getUserType() {
        return userType;
    }

    public void setUserType(UserType userType) {
        this.userType = userType;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    @Override
    public String toString() {
        return String.format("User [ID: %d | Name: %s | Role: %s | Location: %s | Email: %s | Contact: %s]",
                userId, userName, userType, location != null ? location : "N/A", email, contactInfo != null ? contactInfo : "N/A");
    }
}
