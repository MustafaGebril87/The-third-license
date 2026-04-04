package com.thethirdlicense.Util;
import java.math.BigDecimal;
import java.util.UUID;

public class TransferRequest {
    private UUID recipientId;
    private double amount;
	public UUID getRecipientId() {
		return this.recipientId;
	}

	public void setRecipientId(UUID recipientId) {
		this.recipientId = recipientId;
	}

	public double getAmount() {
		return this.amount;
	}

	public void setAmount(double amount) {
		this.amount = amount;
	}
}
