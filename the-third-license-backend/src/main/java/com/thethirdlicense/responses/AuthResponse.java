package com.thethirdlicense.responses;

import com.thethirdlicense.models.User;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class AuthResponse {
    private UUID id;
    private String username;
    private String email;
    private Set<String> roles;

    public AuthResponse(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.roles = user.getRoles().stream()
                .map(Enum::name)
                .collect(Collectors.toSet());
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public Set<String> getRoles() { return roles; }
}
