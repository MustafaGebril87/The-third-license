package com.thethirdlicense.models;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
@Entity
public class Token {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(columnDefinition = "uuid", updatable = false, nullable = false)
	private UUID id;

    @Column(unique = true, nullable = false)
    private String tokenValue;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = true) // Nullable for external use
    private User owner; 

    @ManyToOne
    @JoinColumn(name = "external_client_id", nullable = true) // Nullable for internal use
    private ExternalClient externalClient; 

    @Column(nullable = false)
    private double amount;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(nullable = false)
    private boolean used = false; // Prevent double spending

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

	public void setTokenValue(String tokenValue2) {
		this.tokenValue = tokenValue2;
		
	}

	public void setOwner(User user) {
		this.owner = user;
		
	}

	public void setAmount(Double amount2) {
		this.amount = amount2;
		
	}

	public void setExternalClient(ExternalClient client) {
		this.externalClient = client;
		
	}

	public boolean isRevoked() {
		
		return revoked;
	}

	public void setRevoked(boolean b) {
		this.revoked =  b;
		
	}

	public boolean isUsed() {
		
		return used;
	}

	public void setUsed(boolean b) {
		this.used = b;
		
	}
	
	public Double getAmount() {
		
		return this.amount;
	}

	public String getTokenValue() {// TODO Auto-generated method stub
		return tokenValue;
	}

	public LocalDateTime getCreatedAt() {
		// TODO Auto-generated method stub
		return this.createdAt;
	}

	public UUID getId() {
		// TODO Auto-generated method stub
		return id;
	}
	public User getOwner() { return owner; }

    // Getters and Setters
}
