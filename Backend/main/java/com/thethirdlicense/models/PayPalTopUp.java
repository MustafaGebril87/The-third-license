package com.thethirdlicense.models;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.*;

@Entity
@Table(name = "paypal_topups")
public class PayPalTopUp {

    public enum Status { CREATED, CAPTURED, CANCELLED }

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String paypalOrderId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private double coinAmount;

    @Column(nullable = false)
    private double fee;

    @Column(nullable = false)
    private double totalUsd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.CREATED;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // getters/setters
    public UUID getId() { return id; }

    public String getPaypalOrderId() { return paypalOrderId; }
    public void setPaypalOrderId(String paypalOrderId) { this.paypalOrderId = paypalOrderId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public double getCoinAmount() { return coinAmount; }
    public void setCoinAmount(double coinAmount) { this.coinAmount = coinAmount; }

    public double getFee() { return fee; }
    public void setFee(double fee) { this.fee = fee; }

    public double getTotalUsd() { return totalUsd; }
    public void setTotalUsd(double totalUsd) { this.totalUsd = totalUsd; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
