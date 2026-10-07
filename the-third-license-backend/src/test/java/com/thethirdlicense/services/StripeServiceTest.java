package com.thethirdlicense.services;

import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.thethirdlicense.controllers.StripeCheckoutResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class StripeServiceTest {

    private final StripeService stripeService = new StripeService();

    @Test
    void createCheckoutSession_negativeAmount_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> stripeService.createCheckoutSession(new BigDecimal("-5"), "acct_1", "http://success", "http://cancel", Map.of()));
    }

    @Test
    void createCheckoutSession_zeroAmount_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> stripeService.createCheckoutSession(BigDecimal.ZERO, "acct_1", "http://success", "http://cancel", Map.of()));
    }

    @Test
    void createCheckoutSession_withoutSellerAccount_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> stripeService.createCheckoutSession(new BigDecimal("10"), null, "http://success", "http://cancel", Map.of()));
    }

    @Test
    void fees_areComputedInExactCents() {
        assertThat(stripeService.sellerAmountCents(new BigDecimal("50.00"))).isEqualTo(5000);
        assertThat(stripeService.platformFeeCents(new BigDecimal("50.00"))).isEqualTo(50);   // 1%
        assertThat(stripeService.platformFeeCents(new BigDecimal("0.50"))).isEqualTo(1);     // 0.5c rounds up
        assertThat(stripeService.sellerAmountCents(new BigDecimal("19.99"))).isEqualTo(1999);
    }

    @Test
    void createCheckoutSession_routesPriceToSellerAndKeepsFee() throws StripeException {
        try (MockedStatic<Session> sessionStatic = mockStatic(Session.class)) {
            Session mockSession = mock(Session.class);
            when(mockSession.getId()).thenReturn("cs_test_abc");
            when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/pay/cs_test_abc");
            ArgumentCaptor<SessionCreateParams> captor = ArgumentCaptor.forClass(SessionCreateParams.class);
            sessionStatic.when(() -> Session.create(captor.capture())).thenReturn(mockSession);

            StripeCheckoutResponse response = stripeService.createCheckoutSession(
                new BigDecimal("100.00"), "acct_seller", "http://localhost:5173/stripe/success",
                "http://localhost:5173/stripe/cancel", Map.of("share_id", "s1"));

            assertThat(response.getSessionId()).isEqualTo("cs_test_abc");
            assertThat(response.getCheckoutUrl()).isEqualTo("https://checkout.stripe.com/pay/cs_test_abc");

            SessionCreateParams params = captor.getValue();
            assertThat(params.getPaymentIntentData().getTransferData().getDestination()).isEqualTo("acct_seller");
            assertThat(params.getPaymentIntentData().getApplicationFeeAmount()).isEqualTo(100L);
            assertThat(params.getLineItems().get(0).getPriceData().getUnitAmount()).isEqualTo(10_100L);
            assertThat(params.getSuccessUrl()).isEqualTo("http://localhost:5173/stripe/success?session_id={CHECKOUT_SESSION_ID}");
            assertThat(params.getMetadata()).containsEntry("share_id", "s1");
        }
    }

    @Test
    void refundSession_destinationCharge_reversesTransferAndFee() throws StripeException {
        try (MockedStatic<Refund> refundStatic = mockStatic(Refund.class)) {
            Session session = mock(Session.class);
            when(session.getPaymentIntent()).thenReturn("pi_123");
            ArgumentCaptor<RefundCreateParams> captor = ArgumentCaptor.forClass(RefundCreateParams.class);
            refundStatic.when(() -> Refund.create(captor.capture())).thenReturn(mock(Refund.class));

            stripeService.refundSession(session, true);

            assertThat(captor.getValue().getPaymentIntent()).isEqualTo("pi_123");
            assertThat(captor.getValue().getReverseTransfer()).isTrue();
            assertThat(captor.getValue().getRefundApplicationFee()).isTrue();
        }
    }

    @Test
    void retrieveSession_validId_returnsSession() throws StripeException {
        try (MockedStatic<Session> sessionStatic = mockStatic(Session.class)) {
            Session mockSession = mock(Session.class);
            when(mockSession.getId()).thenReturn("cs_test_xyz");
            sessionStatic.when(() -> Session.retrieve(anyString())).thenReturn(mockSession);

            Session result = stripeService.retrieveSession("cs_test_xyz");

            assertThat(result.getId()).isEqualTo("cs_test_xyz");
            sessionStatic.verify(() -> Session.retrieve("cs_test_xyz"));
        }
    }
}
