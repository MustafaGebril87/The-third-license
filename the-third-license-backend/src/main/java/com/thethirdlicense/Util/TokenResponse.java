package com.thethirdlicense.Util;
import java.math.BigDecimal;

public class TokenResponse {
    private String token;
    private BigDecimal amount;

    public TokenResponse(String token, BigDecimal amount) {
        this.token = token;
        this.amount = amount;
    }

    // Getters and Setters
}
