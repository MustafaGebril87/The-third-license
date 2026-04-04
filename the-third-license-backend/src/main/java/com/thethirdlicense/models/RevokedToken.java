package com.thethirdlicense.models;
import java.util.Date;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class RevokedToken {

    @Id
    private String token;

    private Date revokedAt;

    public RevokedToken() {}

    public RevokedToken(String token, Date revokedAt) {
        this.token = token;
        this.revokedAt = revokedAt;
    }

    // Getters and Setters
}
