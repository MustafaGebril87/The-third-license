package com.thethirdlicense.services;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.paypal.core.PayPalHttpClient;
import com.paypal.http.HttpResponse;
import com.paypal.orders.AmountBreakdown;
import com.paypal.orders.AmountWithBreakdown;
import com.paypal.orders.ApplicationContext;
import com.paypal.orders.LinkDescription;
import com.paypal.orders.Money;
import com.paypal.orders.Order;
import com.paypal.orders.OrderRequest;
import com.paypal.orders.OrdersCaptureRequest;
import com.paypal.orders.OrdersCreateRequest;
import com.paypal.orders.PurchaseUnitRequest;
import com.thethirdlicense.controllers.PayPalCreateOrderResponse;

@Service
public class PayPalService {

    @Autowired
    private PayPalHttpClient payPalClient;

    /**
     * Creates a PayPal order and returns both:
     * - orderId (needed for capture)
     * - approvalUrl (redirect the user to PayPal)
     *
     * amount = "item total" in USD (your app), fee = 1% handling, total = amount + fee.
     */
    public PayPalCreateOrderResponse createOrder(double amount, String returnUrl, String cancelUrl) throws IOException {
        if (amount <= 0.0) {
            throw new IllegalArgumentException("Amount must be > 0");
        }

        OrderRequest orderRequest = new OrderRequest();
        orderRequest.checkoutPaymentIntent("CAPTURE");

        ApplicationContext context = new ApplicationContext()
                .returnUrl(returnUrl)
                .cancelUrl(cancelUrl)
                .brandName("The Third License")
                .landingPage("LOGIN")
                .shippingPreference("NO_SHIPPING");

        // 1% fee
        double fee = amount * 0.01;
        double total = amount + fee;

        // Round to 2 decimals for PayPal
        String amountStr = String.format("%.2f", amount);
        String feeStr = String.format("%.2f", fee);
        String totalStr = String.format("%.2f", total);

        List<PurchaseUnitRequest> units = List.of(
                new PurchaseUnitRequest()
                        .amountWithBreakdown(new AmountWithBreakdown()
                                .currencyCode("USD")
                                .value(totalStr)
                                .amountBreakdown(new AmountBreakdown()
                                        .itemTotal(new Money().currencyCode("USD").value(amountStr))
                                        .handling(new Money().currencyCode("USD").value(feeStr))
                                )
                        )
        );

        orderRequest.applicationContext(context);
        orderRequest.purchaseUnits(units);

        OrdersCreateRequest request = new OrdersCreateRequest().requestBody(orderRequest);
        HttpResponse<Order> response = payPalClient.execute(request);

        String approvalUrl = null;
        for (LinkDescription link : response.result().links()) {
            if ("approve".equalsIgnoreCase(link.rel())) {
                approvalUrl = link.href();
                break;
            }
        }

        if (approvalUrl == null) {
            throw new IllegalStateException("No approval URL found");
        }

        String orderId = response.result().id();
        return new PayPalCreateOrderResponse(orderId, approvalUrl);
    }

    /**
     * Captures a PayPal order after the user approves it.
     */
    public Order captureOrder(String orderId) throws IOException {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId is required");
        }

        OrdersCaptureRequest request = new OrdersCaptureRequest(orderId);
        request.requestBody(new OrderRequest());

        HttpResponse<Order> response = payPalClient.execute(request);
        return response.result();
    }
}
