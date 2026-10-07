package com.thethirdlicense.services;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.model.Event;
import com.stripe.model.LoginLink;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.thethirdlicense.controllers.StripeCheckoutResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/** Thin wrapper around the Stripe SDK so business logic can be unit-tested with a mock. */
@Service
public class StripeService {

    @Value("${app.marketplace.fee-percent:1.0}")
    private BigDecimal feePercent = new BigDecimal("1.0");

    @Value("${app.stripe.connect-country:US}")
    private String connectCountry = "US";

    @Value("${stripe.webhook.secret:}")
    private String webhookSecret = "";

    /** Price in cents the seller receives. */
    public long sellerAmountCents(BigDecimal price) {
        return price.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** Platform fee in cents charged to the buyer on top of the price. */
    public long platformFeeCents(BigDecimal price) {
        return price.multiply(feePercent).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /**
     * Creates a checkout session for a share purchase as a Connect destination charge:
     * the buyer pays price + fee, the price is transferred to the seller's connected account,
     * and the fee stays with the platform.
     */
    public StripeCheckoutResponse createCheckoutSession(BigDecimal price, String sellerAccountId,
                                                        String successUrl, String cancelUrl,
                                                        Map<String, String> metadata) throws StripeException {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be > 0");
        }
        if (sellerAccountId == null || sellerAccountId.isBlank()) {
            throw new IllegalArgumentException("Seller has no connected Stripe account");
        }

        long feeCents = platformFeeCents(price);
        long totalCents = sellerAmountCents(price) + feeCents;

        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .setSuccessUrl(successUrl + (successUrl.contains("?") ? "&" : "?") + "session_id={CHECKOUT_SESSION_ID}")
            .setCancelUrl(cancelUrl)
            .putAllMetadata(metadata)
            .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                .setApplicationFeeAmount(feeCents)
                .setTransferData(SessionCreateParams.PaymentIntentData.TransferData.builder()
                    .setDestination(sellerAccountId)
                    .build())
                .putAllMetadata(metadata)
                .build())
            .addLineItem(SessionCreateParams.LineItem.builder()
                .setQuantity(1L)
                .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                    .setCurrency("usd")
                    .setUnitAmount(totalCents)
                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                        .setName("Share Purchase")
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

    /**
     * Fully refunds the payment behind a completed checkout session. For destination charges the
     * transfer to the seller and the platform fee are reversed too, so nobody keeps the money.
     */
    public Refund refundSession(Session session, boolean destinationCharge) throws StripeException {
        if (session.getPaymentIntent() == null) {
            throw new IllegalStateException("Session has no payment to refund");
        }
        RefundCreateParams.Builder params = RefundCreateParams.builder()
                .setPaymentIntent(session.getPaymentIntent());
        if (destinationCharge) {
            params.setReverseTransfer(true).setRefundApplicationFee(true);
        }
        return Refund.create(params.build());
    }

    // ── Connect: seller accounts ─────────────────────────────────────────────

    public Account createExpressAccount(String email) throws StripeException {
        return Account.create(AccountCreateParams.builder()
                .setType(AccountCreateParams.Type.EXPRESS)
                .setCountry(connectCountry)
                .setEmail(email)
                .setCapabilities(AccountCreateParams.Capabilities.builder()
                        .setTransfers(AccountCreateParams.Capabilities.Transfers.builder().setRequested(true).build())
                        .setCardPayments(AccountCreateParams.Capabilities.CardPayments.builder().setRequested(true).build())
                        .build())
                .build());
    }

    public Account retrieveAccount(String accountId) throws StripeException {
        return Account.retrieve(accountId);
    }

    /** True when the account can receive destination-charge transfers and pay out to the bank. */
    public static boolean canReceivePayouts(Account account) {
        return Boolean.TRUE.equals(account.getPayoutsEnabled())
                && account.getCapabilities() != null
                && "active".equals(account.getCapabilities().getTransfers());
    }

    public String createOnboardingLink(String accountId, String refreshUrl, String returnUrl) throws StripeException {
        return AccountLink.create(AccountLinkCreateParams.builder()
                .setAccount(accountId)
                .setRefreshUrl(refreshUrl)
                .setReturnUrl(returnUrl)
                .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                .build()).getUrl();
    }

    public String createDashboardLink(String accountId) throws StripeException {
        return LoginLink.createOnAccount(accountId).getUrl();
    }

    // ── Webhooks ─────────────────────────────────────────────────────────────

    public boolean isWebhookConfigured() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }

    /** Verifies the Stripe-Signature header and parses the event. */
    public Event constructWebhookEvent(String payload, String signatureHeader) throws SignatureVerificationException {
        return Webhook.constructEvent(payload, signatureHeader, webhookSecret);
    }
}
