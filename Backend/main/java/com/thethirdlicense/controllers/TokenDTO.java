package com.thethirdlicense.controllers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class TokenDTO {
    private UUID id;
    private String tokenValue;
    private Double amount;
    private boolean revoked;
    private boolean used;
    private LocalDateTime createdAt;

    public TokenDTO(UUID id, String tokenValue, Double double1, boolean revoked, boolean used, LocalDateTime createdAt) {
        this.id = id;
        this.tokenValue = tokenValue;
        this.amount = double1;
        this.revoked = revoked;
        this.used = used;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getTokenValue() { return tokenValue; }
    public double getAmount() { return amount; }
    public boolean isRevoked() { return revoked; }
    public boolean isUsed() { return used; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
