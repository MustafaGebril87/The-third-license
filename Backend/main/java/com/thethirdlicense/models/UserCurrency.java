package com.thethirdlicense.models;
import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_currency")
public class UserCurrency {

    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;


    @Column(nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;

    public UserCurrency() {
    }
    public UserCurrency(UUID userId, BigDecimal balance) {
        this.id = userId;
        this.balance = balance;
    }
    public UserCurrency(User user, BigDecimal balance) {
        this.user = user;
        this.balance = balance;
    }

    public UUID getId() {
        return id;
    }

    

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
	public void setUser(User user2) {
		this.user = user2;
		
	}

   
   
}
