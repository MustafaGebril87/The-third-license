package com.thethirdlicense.controllers;

public class PayPalCreateOrderResponse {
    private String orderId;
    private String approvalUrl;

    public PayPalCreateOrderResponse(String orderId, String approvalUrl) {
        this.orderId = orderId;
        this.approvalUrl = approvalUrl;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getApprovalUrl() {
        return approvalUrl;
    }
}
