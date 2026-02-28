package com.thethirdlicense.controllers;

import java.util.UUID;

import com.thethirdlicense.models.User;

/**
 * Data Transfer Object for User registration, authentication, and Git credentials.
 */
public class UserDTO {
    private UUID id;

    private String username;
    private String password;
    private String email;
    private String fullName;

    private String gitUsername;
    private String gitToken;

    // === Getters and Setters ===
    public UserDTO() {} 
    public UserDTO(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
    }
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }
    public UUID getId() {
        return id;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getGitUsername() {
        return gitUsername;
    }

    public void setGitUsername(String gitUsername) {
        this.gitUsername = gitUsername;
    }

    public String getGitToken() {
        return gitToken;
    }

    public void setGitToken(String gitToken) {
        this.gitToken = gitToken;
    }
}
