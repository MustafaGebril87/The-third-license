package com.thethirdlicense.controllers;

public class StripeCheckoutResponse {
    private String sessionId;
    private String checkoutUrl;

    public StripeCheckoutResponse(String sessionId, String checkoutUrl) {
        this.sessionId = sessionId;
        this.checkoutUrl = checkoutUrl;
    }

    public String getSessionId() { return sessionId; }
    public String getCheckoutUrl() { return checkoutUrl; }
}
