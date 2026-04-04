package com.thethirdlicense.models;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class CurrencyTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = true)
    private User user; // Nullable if external transaction

    @ManyToOne
    @JoinColumn(name = "external_client_id", nullable = true)
    private ExternalClient externalClient; // Nullable if internal transaction

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private boolean approvd;
    
    @Column(nullable = false)
    private LocalDateTime transactionDate = LocalDateTime.now();

	public void setUser(User user2) {
		this.user = user2;
		
	}

	public void setAmount(BigDecimal amount2) {
		this.amount = amount2;
		
	}

	public void setDescription(String description2) {
		this.description = description2;
		
	}

	public void setExternalClient(ExternalClient client) {
		this.externalClient = client;
		
	}

	public void setApproved(boolean b) {
		this.approvd = b;
		
	}

    // Getters and Setters
}
