package com.thethirdlicense.exceptions;

/** Thrown when a paid share purchase could not be completed and the payment was refunded. */
public class PurchaseRefundedException extends RuntimeException {
    public PurchaseRefundedException(String message) {
        super(message);
    }
}
