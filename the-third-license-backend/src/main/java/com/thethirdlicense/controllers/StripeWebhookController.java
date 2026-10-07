package com.thethirdlicense.controllers;

import com.google.gson.JsonParser;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.thethirdlicense.exceptions.PurchaseRefundedException;
import com.thethirdlicense.services.PayoutService;
import com.thethirdlicense.services.ShareStripeService;
import com.thethirdlicense.services.StripeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Stripe webhook endpoint (public — authenticated by the Stripe-Signature header instead of a JWT).
 * Completes share purchases even if the buyer never returns to the success page, and keeps
 * sellers' payout status in sync.
 */
@RestController
@RequestMapping("/api/stripe/webhook")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final StripeService stripeService;
    private final ShareStripeService shareStripeService;
    private final PayoutService payoutService;

    public StripeWebhookController(StripeService stripeService, ShareStripeService shareStripeService,
                                   PayoutService payoutService) {
        this.stripeService = stripeService;
        this.shareStripeService = shareStripeService;
        this.payoutService = payoutService;
    }

    @PostMapping
    public ResponseEntity<String> handle(@RequestBody String payload,
                                         @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        if (!stripeService.isWebhookConfigured()) {
            log.error("Stripe webhook received but STRIPE_WEBHOOK_SECRET is not set");
            return ResponseEntity.status(503).body("Webhook not configured");
        }

        Event event;
        try {
            event = stripeService.constructWebhookEvent(payload, signature);
        } catch (SignatureVerificationException | RuntimeException e) {
            log.warn("Rejected Stripe webhook: invalid signature");
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        try {
            switch (event.getType()) {
                case "checkout.session.completed", "checkout.session.async_payment_succeeded" ->
                        shareStripeService.completeFromWebhook(objectId(event));
                case "account.updated" -> payoutService.handleAccountUpdated(objectId(event));
                default -> { /* not subscribed / not relevant */ }
            }
        } catch (PurchaseRefundedException e) {
            log.info("Share purchase refunded from webhook: {}", e.getMessage());
        } catch (Exception e) {
            // 500 makes Stripe retry later
            log.error("Failed to process Stripe event {} ({})", event.getId(), event.getType(), e);
            return ResponseEntity.status(500).body("Processing failed");
        }
        return ResponseEntity.ok("ok");
    }

    /** id of the event's data.object, read from raw JSON so it works across Stripe API versions. */
    private static String objectId(Event event) {
        String raw = event.getDataObjectDeserializer().getRawJson();
        return JsonParser.parseString(raw).getAsJsonObject().get("id").getAsString();
    }
}
