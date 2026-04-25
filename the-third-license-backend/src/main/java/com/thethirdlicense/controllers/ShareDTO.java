package com.thethirdlicense.controllers;

import com.thethirdlicense.models.Share;

import java.math.BigDecimal;
import java.util.UUID;

public class ShareDTO {
    private UUID id;
    private String companyName;
    private double percentage;
    private boolean isForSale;
    private BigDecimal price;
    private UUID ownerId;
    private String ownerUsername;

    public ShareDTO(Share share) {
        this.id = share.getId();
        this.companyName = share.getCompany().getName();
        this.percentage = share.getPercentage();
        this.isForSale = share.isForSale();
        this.price = share.getPrice();
        this.ownerId = share.getOwner().getId();
        this.ownerUsername = share.getOwner().getUsername();
    }

    public UUID getId() {
        return id;
    }

    public String getCompanyName() {
        return companyName;
    }

    public double getPercentage() {
        return percentage;
    }

    public boolean isForSale() {
        return isForSale;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }
}
