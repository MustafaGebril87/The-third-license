package com.thethirdlicense.services;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.thethirdlicense.controllers.StripeCheckoutResponse;
import org.springframework.stereotype.Service;

@Service
public class StripeService {

    public StripeCheckoutResponse createCheckoutSession(double amount, String successUrl, String cancelUrl) throws StripeException {
        if (amount <= 0.0) {
            throw new IllegalArgumentException("Amount must be > 0");
        }

        // 1% fee
        double total = amount * 1.01;
        long totalCents = Math.round(total * 100);

        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
            .setCancelUrl(cancelUrl)
            .addLineItem(SessionCreateParams.LineItem.builder()
                .setQuantity(1L)
                .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                    .setCurrency("usd")
                    .setUnitAmount(totalCents)
                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                        .setName("Coin Top-Up")
                        .build())
                    .build())
                .build())
            .build();

        Session session = Session.create(params);
        return new StripeCheckoutResponse(session.getId(), session.getUrl());
    }

    public Session retrieveSession(String sessionId) throws StripeException {
        return Session.retrieve(sessionId);
    }
}
