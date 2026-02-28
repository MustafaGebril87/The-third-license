package com.thethirdlicense.services;

import java.io.IOException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.paypal.orders.Order;
import com.paypal.orders.PurchaseUnit;
import com.thethirdlicense.controllers.PayPalCreateOrderResponse;
import com.thethirdlicense.models.PayPalTopUp;
import com.thethirdlicense.repositories.PayPalTopUpRepository;

import jakarta.transaction.Transactional;

@Service
public class TokenTopUpService {

    private final PayPalService payPalService;
    private final TokenService tokenService;
    private final PayPalTopUpRepository topUpRepository;

    @Autowired
    public TokenTopUpService(PayPalService payPalService, TokenService tokenService, PayPalTopUpRepository topUpRepository) {
        this.payPalService = payPalService;
        this.tokenService = tokenService;
        this.topUpRepository = topUpRepository;
    }

    public PayPalCreateOrderResponse createTopUpOrder(UUID userId, double coinAmount, String returnUrl, String cancelUrl)
            throws IOException {

        if (coinAmount <= 0.0) throw new IllegalArgumentException("Invalid coin amount");

        // Your PayPalService treats 'amount' as USD itemTotal and adds 1% fee.
        // Here we assume: 1 coin == 1 USD (change later if you want exchange rate).
        PayPalCreateOrderResponse created = payPalService.createOrder(coinAmount, returnUrl, cancelUrl);

        double fee = coinAmount * 0.01;
        double total = coinAmount + fee;

        PayPalTopUp topUp = new PayPalTopUp();
        topUp.setPaypalOrderId(created.getOrderId());
        topUp.setUserId(userId);
        topUp.setCoinAmount(coinAmount);
        topUp.setFee(fee);
        topUp.setTotalUsd(total);
        topUp.setStatus(PayPalTopUp.Status.CREATED);

        topUpRepository.save(topUp);

        return created;
    }

    @Transactional
    public void captureTopUp(UUID userId, String orderId) throws IOException {

        Order order = payPalService.captureOrder(orderId);

        if (!"COMPLETED".equalsIgnoreCase(order.status())) {
            throw new IllegalStateException("PayPal order not completed");
        }

        // Get first capture
        var purchaseUnit = order.purchaseUnits().get(0);
        var capture = purchaseUnit.payments().captures().get(0);

        String currency = capture.amount().currencyCode();
        double paidUsd = Double.parseDouble(capture.amount().value());

        if (!"USD".equalsIgnoreCase(currency)) {
            throw new IllegalStateException("Unexpected currency: " + currency);
        }

        // Example conversion: 1 USD = 10 coins
        double coinsToCredit = paidUsd * 10;

        tokenService.generateTokenForUser(userId, coinsToCredit);
    }


}
