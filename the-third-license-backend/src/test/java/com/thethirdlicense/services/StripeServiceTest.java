package com.thethirdlicense.services;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.thethirdlicense.controllers.StripeCheckoutResponse;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class StripeServiceTest {

    private final StripeService stripeService = new StripeService();

    @Test
    void createCheckoutSession_negativeAmount_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> stripeService.createCheckoutSession(-5.0, "http://success", "http://cancel"));
    }

    @Test
    void createCheckoutSession_zeroAmount_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> stripeService.createCheckoutSession(0.0, "http://success", "http://cancel"));
    }

    @Test
    void createCheckoutSession_validAmount_returnsResponseWithSessionIdAndUrl() throws StripeException {
        try (MockedStatic<Session> sessionStatic = mockStatic(Session.class)) {
            Session mockSession = mock(Session.class);
            org.mockito.Mockito.when(mockSession.getId()).thenReturn("cs_test_abc");
            org.mockito.Mockito.when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/pay/cs_test_abc");
            sessionStatic.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            StripeCheckoutResponse response = stripeService.createCheckoutSession(
                100.0, "http://localhost:5173/stripe/success", "http://localhost:5173/stripe/cancel");

            assertThat(response.getSessionId()).isEqualTo("cs_test_abc");
            assertThat(response.getCheckoutUrl()).isEqualTo("https://checkout.stripe.com/pay/cs_test_abc");
            // verify Session.create was actually called
            sessionStatic.verify(() -> Session.create(any(SessionCreateParams.class)));
        }
    }

    @Test
    void retrieveSession_validId_returnsSession() throws StripeException {
        try (MockedStatic<Session> sessionStatic = mockStatic(Session.class)) {
            Session mockSession = mock(Session.class);
            org.mockito.Mockito.when(mockSession.getId()).thenReturn("cs_test_xyz");
            sessionStatic.when(() -> Session.retrieve(anyString())).thenReturn(mockSession);

            Session result = stripeService.retrieveSession("cs_test_xyz");

            assertThat(result.getId()).isEqualTo("cs_test_xyz");
            sessionStatic.verify(() -> Session.retrieve("cs_test_xyz"));
        }
    }
}
