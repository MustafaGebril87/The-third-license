package com.thethirdlicense.models;

import java.util.UUID;

public class ShareTransactionRequest {
    private UUID  shareId;  // The ID of the share being bought
    private int quantity;  // Number of shares to buy
    private Double price;  // Price per share

    // Getters & Setters
    public UUID getShareId() {
        return shareId;
    }

    public void setShareId(UUID  shareId) {
        this.shareId = shareId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

	public int getAmount() {
		// TODO Auto-generated method stub
		return quantity;
	}
}
