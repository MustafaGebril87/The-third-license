package com.thethirdlicense.services;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.thethirdlicense.controllers.StripeCheckoutResponse;
import com.thethirdlicense.models.StripeTopUp;
import com.thethirdlicense.repositories.StripeTopUpRepository;
import jakarta.transaction.Transactional;

@Service
public class TokenTopUpService {

    private final StripeService stripeService;
    private final TokenService tokenService;
    private final StripeTopUpRepository topUpRepository;

    @Autowired
    public TokenTopUpService(StripeService stripeService, TokenService tokenService, StripeTopUpRepository topUpRepository) {
        this.stripeService = stripeService;
        this.tokenService = tokenService;
        this.topUpRepository = topUpRepository;
    }

    public StripeCheckoutResponse createTopUpOrder(UUID userId, double coinAmount, String successUrl, String cancelUrl)
            throws StripeException {
        if (coinAmount <= 0.0) throw new IllegalArgumentException("Invalid coin amount");

        StripeCheckoutResponse created = stripeService.createCheckoutSession(coinAmount, successUrl, cancelUrl);

        double fee = coinAmount * 0.01;
        double total = coinAmount + fee;

        StripeTopUp topUp = new StripeTopUp();
        topUp.setStripeSessionId(created.getSessionId());
        topUp.setUserId(userId);
        topUp.setCoinAmount(coinAmount);
        topUp.setFee(fee);
        topUp.setTotalUsd(total);
        topUp.setStatus(StripeTopUp.Status.CREATED);

        topUpRepository.save(topUp);
        return created;
    }

    @Transactional
    public void captureTopUp(UUID userId, String sessionId) throws StripeException {
        Session session = stripeService.retrieveSession(sessionId);

        if (!"complete".equalsIgnoreCase(session.getStatus()) ||
                !"paid".equalsIgnoreCase(session.getPaymentStatus())) {
            throw new IllegalStateException("Stripe session not completed");
        }

        double paidUsd = session.getAmountTotal() / 100.0;

        // 1 USD = 10 coins
        double coinsToCredit = paidUsd * 10;
        tokenService.generateTokenForUser(userId, coinsToCredit);
    }
}
