package com.thethirdlicense.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "currency_tokens")
public class CurrencyToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private BigDecimal amount;

    public CurrencyToken() {
    }

    public CurrencyToken(User user, String token, BigDecimal amount) {
        this.user = user;
        this.token = token;
        this.amount = amount;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    @JsonProperty("tokenValue")
    public String getToken() {
        return token;
    }

    public BigDecimal getAmount() {
        return amount;
    }

	public void setAmount(BigDecimal remaining) {
		this.amount = remaining;
		
	}
}
