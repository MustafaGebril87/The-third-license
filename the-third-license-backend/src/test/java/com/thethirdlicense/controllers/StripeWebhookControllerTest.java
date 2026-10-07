package com.thethirdlicense.controllers;

import com.stripe.net.Webhook;
import com.thethirdlicense.services.PayoutService;
import com.thethirdlicense.services.ShareStripeService;
import com.thethirdlicense.services.StripeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Uses the real StripeService for signature verification (offline HMAC), mocks the business services. */
class StripeWebhookControllerTest {

    private static final String SECRET = "whsec_unit_test_secret";

    private StripeService stripeService;
    private ShareStripeService shareStripeService;
    private PayoutService payoutService;
    private StripeWebhookController controller;

    @BeforeEach
    void setUp() {
        stripeService = new StripeService();
        ReflectionTestUtils.setField(stripeService, "webhookSecret", SECRET);
        shareStripeService = mock(ShareStripeService.class);
        payoutService = mock(PayoutService.class);
        controller = new StripeWebhookController(stripeService, shareStripeService, payoutService);
    }

    private static String event(String type, String objectId, String objectType) {
        return "{\"id\":\"evt_1\",\"object\":\"event\",\"api_version\":\"2023-10-16\",\"type\":\"" + type
                + "\",\"data\":{\"object\":{\"id\":\"" + objectId + "\",\"object\":\"" + objectType + "\"}}}";
    }

    private static String sign(String payload, String secret) throws Exception {
        long t = Webhook.Util.getTimeNow();
        return "t=" + t + ",v1=" + Webhook.Util.computeHmacSha256(secret, t + "." + payload);
    }

    @Test
    void checkoutCompleted_withValidSignature_completesPurchase() throws Exception {
        String payload = event("checkout.session.completed", "cs_test_1", "checkout.session");

        ResponseEntity<String> resp = controller.handle(payload, sign(payload, SECRET));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(shareStripeService).completeFromWebhook("cs_test_1");
    }

    @Test
    void accountUpdated_refreshesSellerPayoutStatus() throws Exception {
        String payload = event("account.updated", "acct_123", "account");

        ResponseEntity<String> resp = controller.handle(payload, sign(payload, SECRET));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(payoutService).handleAccountUpdated("acct_123");
    }

    @Test
    void forgedSignature_isRejected_andNothingHappens() throws Exception {
        String payload = event("checkout.session.completed", "cs_test_1", "checkout.session");

        ResponseEntity<String> resp = controller.handle(payload, sign(payload, "whsec_attacker"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(shareStripeService, never()).completeFromWebhook(anyString());
    }

    @Test
    void missingSignature_isRejected() throws Exception {
        String payload = event("checkout.session.completed", "cs_test_1", "checkout.session");

        ResponseEntity<String> resp = controller.handle(payload, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(shareStripeService, never()).completeFromWebhook(anyString());
    }

    @Test
    void notConfigured_returns503() throws Exception {
        ReflectionTestUtils.setField(stripeService, "webhookSecret", "");
        String payload = event("checkout.session.completed", "cs_test_1", "checkout.session");

        ResponseEntity<String> resp = controller.handle(payload, sign(payload, SECRET));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void processingFailure_returns500SoStripeRetries() throws Exception {
        doThrow(new RuntimeException("db down")).when(shareStripeService).completeFromWebhook("cs_test_1");
        String payload = event("checkout.session.completed", "cs_test_1", "checkout.session");

        ResponseEntity<String> resp = controller.handle(payload, sign(payload, SECRET));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
